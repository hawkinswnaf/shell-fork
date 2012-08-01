#ifndef _PROCESS_H
#define _PROCESS_H

#include <pthread.h>

struct process {
	int input, output, pid;
	char *tag;
	pthread_t output_thread, wait_thread;
	struct process *prev, *next;
};

void add_process(struct process *p);
void remove_process(struct process *p);
struct process *find_process_by_tag(char *tag);
void free_process(struct process *p);
void print_process(struct process *p);
void print_processes(void);
void init_process_list(void);

#endif
