#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdint.h>
#include "process.h"

static struct process *global_process_list;

void init_process_list(void) {
	global_process_list = NULL;
}

void add_process(struct process *p) {
	if (global_process_list)
		global_process_list->prev = p;
	p->next = global_process_list;
	global_process_list = p;
}

void remove_process(struct process *p) {

	if (p->prev == NULL && p->next == NULL) {
		/* one and only node in the list.
		*/
		global_process_list = NULL;
		return;
	}

	if (!p->prev) {
		/* 
		 * we are at the beginning!
		 */
		p->next->prev = NULL;
		global_process_list = p->next;
	} else {
		if (p->next)
			p->next->prev = p->prev;
		p->prev->next = p->next;
	}
}

struct process *find_process_by_tag(char *tag) {
	struct process *iter;
	for (iter = global_process_list; iter!=NULL; iter = iter->next)
		if (!strcmp(tag, iter->tag))
			return iter;
	return NULL;
}

void print_process(struct process *p) {
	printf("process:\n");
	printf("pid:           %d\n", p->pid);
	printf("tag:           %s\n", p->tag);
	printf("input:         %d\n", p->input);
	printf("output:        %d\n", p->output);
	printf("output_thread: %lx\n", (long unsigned int)&p->output_thread);
	printf("wait_thread:   %lx\n", (long unsigned int)&p->wait_thread);
}

void print_processes(void) {
	struct process *iter;

	printf("Processes:\n");
	printf("---------\n");
	for (iter = global_process_list; iter!=NULL; iter = iter->next)
		print_process(iter);
	printf("---------\n");
}

void free_process(struct process *p) {
	free(p->tag);
	free(p);
}
