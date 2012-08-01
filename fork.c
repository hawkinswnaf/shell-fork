#include <stdio.h>
#define _GNU_SOURCE
#include <unistd.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <pthread.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

int global_pipe[2] ;
fd_set global_set;
char **global_envp;

#include "common.h"
#include "process.h"


typedef enum {
	COMMAND = 0,
	TAG = 1,
	EXTRA = 2,
} message_tokens_t;

extern int errno;

int read_message(int client, char **message) {
	int message_len = 0, message_idx = 0; 
	int expected_colons = 3, colon_count = 1;

	message_len = 1;
	message_idx = 0;
	*message = (char*)calloc(message_len, sizeof(char));

	do {
		int just_read = 0;
		if ((just_read = read(client, *message+message_idx, 1))<=0) {
			/*
			 * stop reading early!
			 */
			return -1; 	
		}

		*message=(char*)realloc(*message,sizeof(char)*(message_len+1));
		memset(*message + message_len, 0, sizeof(char));

		if (*(*message + message_idx) == ':') {
			if (colon_count == expected_colons)
				break;
			else 
				colon_count++;
		}

		message_len+=1;
		message_idx+=1;

		fprintf(stderr, "message: %s\n", *message);
	} while (1);
	return 0;
}

void *output_monitor(void *param) {
	struct process *p = (struct process *)param;
	FILE *output = NULL, *global_output;
	int will_break = 0; 
	fd_set read_set, except_set;

	FD_ZERO(&read_set);
	FD_ZERO(&except_set);
	FD_SET(p->output, &read_set);
	FD_SET(p->output, &except_set);

#if 0
	if (!(global_output = fdopen(global_pipe[1], "w"))) {
		fprintf(stderr, "Cannot do fdopen() for global_output\n"); 
		return NULL;
	}
#endif
	DEBUG("Starting output monitor for %s.\n", p->tag);

	while (!will_break) {
		char buffer[80] = {0,};
		struct timeval timeout;
		int read_size = 0;

		timeout.tv_sec = 5;
		timeout.tv_usec = 0;

		select(FD_SETSIZE, &read_set, NULL, &except_set, &timeout);
		
		DEBUG("Done output monitor select()ing\n");

		if (FD_ISSET(p->output, &except_set)) {
			fprintf(stderr, "Exception occurred!\n");
			break;
		}

		if (FD_ISSET(p->output, &read_set)) {
			char *newline = "\n";
			if ((read_size = read(p->output, buffer, 79)) == 0)
				break;

			DEBUG("buffer: %s\n", buffer);
			write(global_pipe[1], p->tag, strlen(p->tag));
			write(global_pipe[1], buffer, read_size);
			write(global_pipe[1], newline, strlen(newline));
#if 0
			fprintf(global_output, "%s:%s\n", p->tag, buffer);
			fflush(global_output);
#endif
			fprintf(stderr, "stderr: %s:%s\n", p->tag, buffer);
			fflush(stderr);
		}
		else {
			fprintf(stderr, "Not in the set.\n");
		}
			
		FD_SET(p->output, &read_set);
		FD_SET(p->output, &except_set);
	}
	remove_process(p);
	
	close(p->input);
	close(p->output);
	
	DEBUG("Stopping output monitor for %s.\n", p->tag);

#ifdef DEBUG
	print_processes();
#endif

	free_process(p);
	return NULL;
}

void *threaded_wait_pid(void *arg) {
	struct process *p = (struct process *)arg;
	int status = 0;

	waitpid(p->pid, &status, 0);

	DEBUG("Child (%d) finished: %d.\n", p->pid, status);
}

void debug_tokenize_cmd(char **argv, int argc) {
	int i = 0;
	fprintf(stderr, "argc: %d\n", argc);
	for (i = 0; i<argc; i++)
		fprintf(stderr, "argv[%d]: -%s-\n", i, (argv[i]) ? argv[i] : "!");
}

int tokenize_cmd(char *cmd, char ***argv, int *argc) {
	char *saveptr = NULL;

	for (*argc = 0, *argv = NULL; ; (*argc)++, cmd = NULL) {
		int will_break = 0;
		char *token = strtok_r(cmd, " ", &saveptr);
		if (token == NULL)
			will_break = 1;
		
		*argv = (char**)realloc(*argv, ((*argc)+1)*sizeof(char**));
		(*argv)[*argc] = token;

		if (will_break)
			break;
	}

	return 0;
}

void handle_stop_cmd(char *tag, char *extra) {
	struct process *p = NULL;

	if (p = find_process_by_tag(tag)) {
		DEBUG("Found process to kill\n");
		if (kill(p->pid, SIGINT)) {
			fprintf(stderr, "kill() failed: %d\n", errno);
		}
	}
}

void handle_input_cmd(char *tag, char *extra) {
	struct process *p = NULL;
	char *newline = "\n";

	if (p = find_process_by_tag(tag)) {
		DEBUG("INPUT extra: %s\n", extra);
		DEBUG("p->input: %d\n", p->input);
		DEBUG("p->output: %d\n", p->output);
#if 0
		//write(p->input, extra, strlen(extra));
		//write(p->input, newline, strlen(newline));
		write(p->input, "any\r\n", 4);
		//close(p->input);
#endif
		FILE *in = NULL;

		if (!(in = fdopen(p->input, "w"))) {
			fprintf(stderr, "Could not send input to process (%s).\n", tag);
			return;
		}
		fprintf(in, "%s\n", extra);
		fflush(in);
		//fclose(in);
	} else {
		fprintf(stderr, "Could not find process (%s) for input.\n",tag);
	}
	return;
}

void handle_start_cmd(char *tag, char *cmd) {
	struct process *p;
	int pid = 0;
	int local_stdin_pipe[2], local_stdout_pipe[2];
	char **tokenized_cmd = NULL;
	int tokenized_cmd_len = 0;

	if (global_pipe[0] == -1 || global_pipe[1] == -1) {
		fprintf(stderr, "Cannot do commands without a listener.\n");
		return;
	}

	p = (struct process*)malloc(sizeof(struct process));

	p->tag = strdup(tag);

	if (pipe(local_stdin_pipe)) 
		fprintf(stderr, "pipe2(): %d", errno);
	else {
		DEBUG("local_stdin_pipe[0]: %d\n", local_stdin_pipe[0]);
		DEBUG("local_stdin_pipe[1]: %d\n", local_stdin_pipe[1]);
	}
	if (pipe(local_stdout_pipe))
		fprintf(stderr, "pipe2(): %d", errno);
	else {
		DEBUG("local_stdout_pipe[0]: %d\n", local_stdout_pipe[0]);
		DEBUG("local_stdout_pipe[1]: %d\n", local_stdout_pipe[1]);
	}

	if (tokenize_cmd(cmd, &tokenized_cmd, &tokenized_cmd_len))
		fprintf(stderr, "Error tokenizing the cmd!\n");

	debug_tokenize_cmd(tokenized_cmd, tokenized_cmd_len);

	if (!(pid = fork())) {
		/* TODO: Support command line arguments!
		 */
		
		dup2(local_stdin_pipe[0], 0);
		dup2(local_stdout_pipe[1], 1);

		close(local_stdin_pipe[0]);
		close(local_stdin_pipe[1]);
		close(local_stdout_pipe[0]);
		close(local_stdout_pipe[1]);

		if (execvp(tokenized_cmd[0], tokenized_cmd)<0) {
			fprintf(stderr, "execvp() failed!\n");
			fflush(stderr);
			exit(1);
		}
	}
	DEBUG("fork()ed: %d\n", pid);
	p->pid = pid;

	p->input = local_stdin_pipe[1];
	p->output = local_stdout_pipe[0];

	close(local_stdin_pipe[0]);
	close(local_stdout_pipe[1]);

	add_process(p);
#ifdef DEBUG
	print_processes();
#endif

	pthread_create(&(p->output_thread), NULL, output_monitor, p);
	pthread_create(&(p->wait_thread), NULL, threaded_wait_pid, p);
}

int setup_server_socket(unsigned short port, unsigned long addr) {
	int server;
	struct sockaddr_in sin;

	memset(&sin, 0, sizeof(sin));
	sin.sin_port = htons(port);
	sin.sin_addr.s_addr = htonl(addr);

	if ((server = socket(AF_INET, SOCK_STREAM|SOCK_CLOEXEC, 0)) == -1) {
		fprintf(stderr, "Error in socket()\n");
		return -1;
	}
	if (bind(server, (struct sockaddr*)&sin, sizeof(sin)) == -1) {
		fprintf(stderr, "Error in bind(): %d\n", errno);
		return -1;
	}
	
	if (listen(server, -1) == -1) {
		fprintf(stderr, "Error in listen()\n");
		return -1;
	}
	return server;
}

void handle_cmd_client(int client) {
	char *message = NULL, *token = NULL, *saveptr = NULL;
	message_tokens_t message_token = COMMAND;
	char *parsed_message[3];
	
	printf("Client connected!\n");
	
	if (read_message(client, &message) < 0) {
		/* 
		 * error occurred reading message.
		 */
		fprintf(stderr, "Error reading message!\n");
		goto out;
	}
	DEBUG("message: -%s-\n", message);

	token = strtok_r(message, ":", &saveptr);
	while (token) {
		DEBUG("token: %s\n", token);
		parsed_message[message_token] = token;
		message_token++;
		token = strtok_r(NULL, ":", &saveptr);
	}

	DEBUG("1. command: %s\n", parsed_message[COMMAND]);
	DEBUG("2. tag    : %s\n", parsed_message[TAG]);
	DEBUG("3. extra  : %s\n", parsed_message[EXTRA]);

	if (!strcmp(parsed_message[COMMAND], "START")) {
		DEBUG("start\n");
		handle_start_cmd(parsed_message[TAG], parsed_message[EXTRA]);
	} else if (!strcmp(parsed_message[COMMAND], "STOP")) {
		DEBUG("stop\n");
		handle_stop_cmd(parsed_message[TAG], parsed_message[EXTRA]);
	} else if (!strcmp(parsed_message[COMMAND], "INPUT")) {
		DEBUG("input\n");
		handle_input_cmd(parsed_message[TAG], parsed_message[EXTRA]);
	} else {
		fprintf(stderr,"Unknown COMMAND: %s\n",parsed_message[COMMAND]);
	}
out:
	shutdown(client, SHUT_RDWR);
	close(client);
}

void handle_io_client(int client) {
	FILE *global_output;
	pipe2(global_pipe, O_CLOEXEC);
	FD_SET(global_pipe[0], &global_set);

	if (!(global_output = fdopen(global_pipe[0], "r"))) {
		fprintf(stderr, "OOPS: Cannot open global output!\n");
		return;
	}

	while (1) {
		char buffer[80];
		struct timeval timeout;

		timeout.tv_sec = 5;
		timeout.tv_usec = 0;

		select(FD_SETSIZE, &global_set, NULL, NULL, &timeout);

		DEBUG("Done global select()ing\n");
		if (FD_ISSET(global_pipe[0], &global_set)) {
			if (fgets(buffer, 80, global_output)) {
				printf("OUTPUT: %s\n", buffer);
			} else {
				fprintf(stderr, "Got EOF from fgets()\n");
				break;
			}
		}
		FD_SET(global_pipe[0], &global_set);
	}
	return;
}

void *dummy_handle_io_client(void *dummy) {
	handle_io_client(0);
}

void *io_listener(void *unused) {
	int server, client;
	struct sockaddr_in child_sin;
	struct sockaddr child_in;
	socklen_t child_in_len = sizeof(struct sockaddr);

	if ((server = setup_server_socket(5001, INADDR_ANY)) < 1) {
		fprintf(stderr, "Error from setup_server_socket()\n");
		return NULL;
	}

	while (client = accept(server, 
			     (struct sockaddr*)&child_in, &child_in_len)) {
		handle_io_client(client);
	}
}

void *command_listener(void *unused) {
	int server, client;
	struct sockaddr_in child_sin;
	struct sockaddr child_in;
	socklen_t child_in_len = sizeof(struct sockaddr);

	if ((server = setup_server_socket(5000, INADDR_ANY)) < 1) {
		fprintf(stderr, "Error from setup_server_socket()\n");
		return NULL;
	}

	while ((client = accept(server, 
			(struct sockaddr*)&child_in, &child_in_len)) >= 0) {
		handle_cmd_client(client);
	}

	fprintf(stderr, "command_listener: %d, %s\n", client, strerror(errno));
	return NULL;
}

int main(int argc, char *argv[], char *envp[]) {
	void *retval;
	pthread_t cmd_server_thread, io_server_thread;

	global_pipe[0] = -1;
	global_pipe[1] = -1;
	FD_ZERO(&global_set);

	global_envp = envp;

	init_process_list();

	if (pthread_create(&cmd_server_thread, NULL, command_listener, NULL)) {
		/* error
		 */
		fprintf(stderr,"pthread_create(cmd_server_thread) failed.\n");
		return 1;
	}
#if 0
	if (pthread_create(&io_server_thread, NULL, io_listener, NULL)) {
		/* error
		 */
		fprintf(stderr, "pthread_create(io_server_thread) failed.\n");
		return 1;
	}
#endif
	if (pthread_create(&io_server_thread, NULL, dummy_handle_io_client, NULL)) {
		fprintf(stderr, "pthread_create(dummy_handle_io_client) failed.\n");
		return 1;
	}

	pthread_join(io_server_thread, &retval);
	pthread_join(cmd_server_thread, &retval);
}
