#define _GNU_SOURCE
#include <stdio.h>
#include <unistd.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <netinet/in.h>
#include <pthread.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>

/*---------------------------------------------
 * Global variables.
 *
 * global_pipe: Pipe for communication between 
 * child processes and the parent I/O wrangler.
 * global_envp: Holds a reference to the envp of
 * this process.
 * global_pipe_output_lock: Locks the output side
 * of the global pipe. This ensures that two 
 * output monitors do not write over each other.
 * global_connection_key: The connection key used
 * to authenticate all client connections.
 *--------------------------------------------*/
int global_pipe[2] ;
char **global_envp;
pthread_mutex_t global_pipe_output_lock, global_output_lock;
char *global_connection_key;
pthread_t global_io_server_thread, global_cmd_server_thread;

#include "common.h"
#include "process.h"

typedef enum {
	COMMAND = 0,
	TAG = 1,
	EXTRA = 2,
} message_tokens_t;

/*---------------------------------------------
 *--------------------------------------------*/
int read_message(int client, char **message) {
	int message_len = 0, message_idx = 0; 
	int colon_count = 0;

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

		if (*(*message + message_idx) == '\n')
			break;
		if (*(*message + message_idx) == ':')
			colon_count++;

		message_len+=1;
		message_idx+=1;

		DEBUG_3("message: %s\n", *message);
	} while (1);
	return colon_count;
}

/*---------------------------------------------
 *--------------------------------------------*/
void *output_monitor(void *param) {
	struct process *p = (struct process *)param;
	FILE *output = NULL, *global_output;
	int will_break = 0; 
	fd_set read_set, except_set;

	FD_ZERO(&read_set);
	FD_ZERO(&except_set);
	FD_SET(p->output, &read_set);
	FD_SET(p->output, &except_set);
	FD_SET(global_pipe[0], &except_set);
	FD_SET(global_pipe[1], &except_set);

	if (!(output = fdopen(p->output, "r"))) {
		eprintf("Cannot do fdopen() for output!\n");
		return NULL;
	}

	if (!(global_output = fdopen(global_pipe[1], "w"))) {
		eprintf("Cannot do fdopen() for global_output\n"); 
		return NULL;
	}
	DEBUG_2("Starting output monitor for %s.\n", p->tag);

	while (!will_break) {
		char buffer[LINE_BUFFER_SIZE+1] = {0,};
		struct timeval timeout;
		int read_size = 0;

		timeout.tv_sec = 5;
		timeout.tv_usec = 0;

		select(FD_SETSIZE, &read_set, NULL, &except_set, &timeout);
		
		DEBUG_3("Done output monitor select()ing\n");

		if (FD_ISSET(p->output, &except_set) ||
		    FD_ISSET(global_pipe[0], &except_set) ||
		    FD_ISSET(global_pipe[1], &except_set)) {
			eprintf("Exception occurred!\n");
			break;
		}

		if (FD_ISSET(p->output, &read_set)) {
			char *newline = "\n";
			if (fgets(buffer, LINE_BUFFER_SIZE, output) == NULL)
				break;

			DEBUG_3("buffer: %s", buffer);
			DEBUG_3("-end of buffer\n");
#if 0
			write(global_pipe[1], p->tag, strlen(p->tag));
			write(global_pipe[1], buffer, read_size);
			write(global_pipe[1], newline, strlen(newline));
#endif
			pthread_mutex_lock(&global_pipe_output_lock);
			fprintf(global_output, "OUTPUT:%s:%s", p->tag, buffer);
			fflush(global_output);
			pthread_mutex_unlock(&global_pipe_output_lock);
#ifdef DEBUG_MODE
			eprintf("stderr: %s:%s\n", p->tag, buffer);
			fflush(stderr);
#endif
		}
		FD_SET(p->output, &read_set);
		FD_SET(p->output, &except_set);
		FD_SET(global_pipe[0], &except_set);
		FD_SET(global_pipe[1], &except_set);
	}
	remove_process(p);
	
	fprintf(global_output, "STOPPED:%s:\n", p->tag);
	fflush(global_output);
	
	close(p->input);
	close(p->output);
	
	DEBUG_2("Stopping output monitor for %s.\n", p->tag);

#ifdef DEBUG
	print_processes();
#endif

	free_process(p);
	return NULL;
}

/*---------------------------------------------
 *--------------------------------------------*/
void *threaded_wait_pid(void *arg) {
	struct process *p = (struct process *)arg;
	int status = 0;

	waitpid(p->pid, &status, 0);

	DEBUG_2("Child (%d) finished: %d.\n", p->pid, status);

	return NULL;
}

/*---------------------------------------------
 *--------------------------------------------*/
void debug_tokenize_cmd(char **argv, int argc) {
	int i = 0;
	dprintf("argc: %d\n", argc);
	for (i = 0; i<argc; i++)
		dprintf("argv[%d]: -%s-\n", i, (argv[i]) ? argv[i] : "!");
}

/*---------------------------------------------
 *--------------------------------------------*/
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
/*---------------------------------------------
 *--------------------------------------------*/
void handle_kill_cmd(char *tag, char *extra) {
	/*
	 * shut it down!
	 */
}

/*---------------------------------------------
 *--------------------------------------------*/
void handle_stop_cmd(char *tag, char *extra) {
	struct process *p = NULL;

	if (p = find_process_by_tag(tag)) {
		DEBUG_3("Found process to kill\n");
//		if (kill(p->pid, SIGINT)) {
		if (kill(p->pid, SIGKILL)) {
			eprintf("kill() failed: %d\n", errno);
		}
	}
}

/*---------------------------------------------
 *--------------------------------------------*/
void handle_input_cmd(char *tag, char *extra) {
	struct process *p = NULL;
	char *newline = "\n";

	if (p = find_process_by_tag(tag)) {
		DEBUG_3("INPUT extra: %s\n", extra);
		DEBUG_3("p->input: %d\n", p->input);
		DEBUG_3("p->output: %d\n", p->output);
#if 0
		//write(p->input, extra, strlen(extra));
		//write(p->input, newline, strlen(newline));
		write(p->input, "any\r\n", 4);
		//close(p->input);
#endif
		FILE *in = NULL;

		if (!(in = fdopen(p->input, "w"))) {
			eprintf("Could not send input to process (%s).\n", tag);
			return;
		}
		fprintf(in, "%s\n", extra);
		fflush(in);
		//fclose(in);
	} else {
		eprintf("Could not find process (%s) for input.\n",tag);
	}
	return;
}

/*---------------------------------------------
 *--------------------------------------------*/
void handle_start_cmd(char *tag, char *cmd) {
	struct process *p;
	int pid = 0;
	int local_stdin_pipe[2], local_stdout_pipe[2];
	char **tokenized_cmd = NULL;
	int tokenized_cmd_len = 0;

	if (global_pipe[0] == GLOBAL_PIPE_UNINITIALIZED || 
	    global_pipe[1] == GLOBAL_PIPE_UNINITIALIZED) {
		eprintf("Cannot do commands without a listener.\n");
		return;
	}

	p = (struct process*)malloc(sizeof(struct process));
	memset(p, 0, sizeof(struct process));

	p->tag = strdup(tag);

	if (pipe(local_stdin_pipe))
		eprintf("pipe2(): %d", errno);
	else {
		DEBUG_3("local_stdin_pipe[0]: %d\n", local_stdin_pipe[0]);
		DEBUG_3("local_stdin_pipe[1]: %d\n", local_stdin_pipe[1]);
	}
	if (pipe(local_stdout_pipe))
		eprintf("pipe2(): %d", errno);
	else {
		DEBUG_3("local_stdout_pipe[0]: %d\n", local_stdout_pipe[0]);
		DEBUG_3("local_stdout_pipe[1]: %d\n", local_stdout_pipe[1]);
	}

	if (tokenize_cmd(cmd, &tokenized_cmd, &tokenized_cmd_len))
		eprintf("Error tokenizing the cmd!\n");

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
			eprintf("execvp() failed!\n");
			fflush(stderr);
			exit(1);
		}
	}
	DEBUG_2("fork()ed: %d\n", pid);
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

/*---------------------------------------------
 *--------------------------------------------*/
int setup_server_socket(unsigned short port, unsigned long addr) {
	int server;
	struct sockaddr_in sin;

	memset(&sin, 0, sizeof(sin));
	sin.sin_port = htons(port);
	sin.sin_addr.s_addr = htonl(addr);
	sin.sin_family = AF_INET;

	if ((server = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)) == -1) {
		eprintf("Error in socket()\n");
		return -1;
	}
	if (fcntl(server, F_SETFD, FD_CLOEXEC)) {
		eprintf("Error in socket()\n");
		return -1;
	}
	if (bind(server, (struct sockaddr*)&sin, sizeof(sin)) == -1) {
		eprintf("Error in bind(): %d\n", errno);
		return -1;
	}
	
	if (listen(server, -1) == -1) {
		eprintf("Error in listen()\n");
		return -1;
	}
	return server;
}

/*---------------------------------------------
 *--------------------------------------------*/
int handle_cmd_client(int client) {
	char *message = NULL, *token = NULL, *saveptr = NULL;
	message_tokens_t message_token = COMMAND;
	char *parsed_message[3] = {NULL,};
	int colons = 0, expected_colons = 3;
	
	DEBUG_2("Client connected!\n");
	
	if ((colons = read_message(client, &message)) < 0) {
		/* 
		 * error occurred reading message.
		 */
		eprintf("Error reading message!\n");
		return 1;
	}
	DEBUG_2("message: -%s-\n", message);
	if (colons != expected_colons) {
		eprintf("Message error: Not enough colons (%d)\n", colons);
		/* TODO
		 * free(message);
		 */
		return 1;
	}

	token = strtok_r(message, ":", &saveptr);
	while (token) {
		DEBUG_3("token: %s\n", token);
		parsed_message[message_token] = token;
		message_token++;
		token = strtok_r(NULL, ":", &saveptr);
	}

	DEBUG_2("1. command: %s\n", parsed_message[COMMAND] ? parsed_message[COMMAND] : "NULL");
	DEBUG_2("2. tag    : %s\n", parsed_message[TAG] ? parsed_message[TAG] : "NULL");
	DEBUG_2("3. extra  : %s\n", parsed_message[EXTRA] ? parsed_message[EXTRA] : "NULL");

	if (!strcmp(parsed_message[COMMAND], "START")) {
		DEBUG_3("start\n");
		handle_start_cmd(parsed_message[TAG], parsed_message[EXTRA]);
	} else if (!strcmp(parsed_message[COMMAND], "STOP")) {
		DEBUG_3("stop\n");
		handle_stop_cmd(parsed_message[TAG], parsed_message[EXTRA]);
	} else if (!strcmp(parsed_message[COMMAND], "INPUT")) {
		DEBUG_3("input\n");
		handle_input_cmd(parsed_message[TAG], parsed_message[EXTRA]);
	} else if (!strcmp(parsed_message[COMMAND], "KILL")) {
		DEBUG_3("kill\n");
		handle_kill_cmd(parsed_message[TAG], parsed_message[EXTRA]);
		return 0;
	} else {
		eprintf("Unknown COMMAND: %s\n",parsed_message[COMMAND]);
	}
	return 1;
	/* TODO
	 * free(message);
	 */
}

/*---------------------------------------------
 *--------------------------------------------*/
void *handle_io_client(void *unused) {
	FILE *global_output, *client_output;
	sigset_t block_sig_set;
	fd_set global_pipe_set;
	

	sigemptyset(&block_sig_set);
	sigaddset(&block_sig_set, SIGPIPE);
	pthread_sigmask(SIG_BLOCK, &block_sig_set, NULL);

//	pipe2(global_pipe, O_CLOEXEC);
	pipe(global_pipe);
	FD_ZERO(&global_pipe_set);
	FD_SET(global_pipe[0], &global_pipe_set);

	if (!(global_output = fdopen(global_pipe[0], "r"))) {
		eprintf("OOPS: Cannot open global output!\n");
		return;
	}
	client_output = stdout;

	while (1) {
		char buffer[LINE_BUFFER_SIZE+1] = {0,};
		struct timeval timeout;

		timeout.tv_sec = 5;
		timeout.tv_usec = 0;

		select(FD_SETSIZE, &global_pipe_set, NULL, NULL, &timeout);

#if 0
This is supposed to handle the case
where an i/o client goes away. 
Unfortunately we cannot detect that 
case with any certainty.
		if (FD_ISSET(client, &client_set)) {
			DEBUG("client disconnected!\n");
			close(global_pipe[0]);	
			close(global_pipe[1]);
			global_pipe[0] = GLOBAL_PIPE_UNINITIALIZED;
			global_pipe[1] = GLOBAL_PIPE_UNINITIALIZED;
			fclose(client_output);
			break;
		}
#endif
		DEBUG_3("Done global select()ing\n");
		if (FD_ISSET(global_pipe[0], &global_pipe_set)) {
			if (fgets(buffer, LINE_BUFFER_SIZE, global_output)) {
				DEBUG_3("DEBUG: %s", buffer);

				pthread_mutex_lock(&global_output_lock);
				fprintf(client_output, "%s", buffer);
				fflush(client_output);
				pthread_mutex_unlock(&global_output_lock);

				if (fflush(client_output)) {
					eprintf("Got error from fflush(client_output)!\n");
					if (errno == EPIPE) {
						eprintf("fflush() failed with EPIPE!\n");
					}
					break;
				}
			} else {
				eprintf("Got EOF from fgets()\n");
				break;
			}
		}
		FD_SET(global_pipe[0], &global_pipe_set);
	}
	DEBUG_3("Stopping handle_io_client.\n");
	close(global_pipe[0]);	
	close(global_pipe[1]);
	global_pipe[0] = GLOBAL_PIPE_UNINITIALIZED;
	global_pipe[1] = GLOBAL_PIPE_UNINITIALIZED;
	fclose(client_output);
	return;
}

/*---------------------------------------------
 *--------------------------------------------*/
void *dummy_handle_io_client(void *dummy) {
	handle_io_client(NULL);
	return NULL;
}

/*---------------------------------------------
 *--------------------------------------------*/
void *command_listener(void *unused) {
	int client = 0;

	while (1)
		if (!handle_cmd_client(client))
			break;
	return NULL;
}


int main(int argc, char *argv[], char *envp[]) {
	void *retval;
	int pthread_error = 0;

	global_pipe[0] = -1;
	global_pipe[1] = -1;

	global_envp = envp;

	init_process_list();

	if (pthread_error = pthread_mutex_init(&global_pipe_output_lock, NULL)) {
		/* error occurred initializing mutex.
		 */
		fprintf(stderr, "pthread_mutex_init(&global_pipe_output_lock) failed: %d\n", pthread_error);
		return 1;
	}

	if (pthread_error = pthread_mutex_init(&global_output_lock, NULL)) {
		/* error occurred initializing mutex.
		 */
		fprintf(stderr, "pthread_mutex_init(&global_output_lock) failed: %d\n", pthread_error);
		return 1;
	}

	if (pthread_error = pthread_create(&global_cmd_server_thread, NULL, command_listener, NULL)) {
		/* error
		 */
		fprintf(stderr,"pthread_create(cmd_server_thread) failed: %d.\n", pthread_error);
		return 1;
	}
#ifdef GLOBAL_OUTPUT
	if (pthread_error = pthread_create(&global_io_server_thread, NULL, handle_io_client, NULL)) {
		/* error
		 */
		fprintf(stderr, "pthread_create(io_server_thread) failed: %d.\n", pthread_error);
		return 1;
	}
#else
	if (pthread_error = pthread_create(&global_io_server_thread, NULL, dummy_handle_io_client, NULL)) {
		fprintf(stderr, "pthread_create(dummy_handle_io_client) failed: %d.\n", pthread_error);
		return 1;
	}

#endif
	pthread_join(global_cmd_server_thread, &retval);

	DEBUG_3("Shutting down properly.\n");
	
	DEBUG_3("Done shutting down properly.\n");

	return 0;
}
