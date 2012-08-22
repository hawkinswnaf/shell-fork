/*---------------------------------------------
 * Process List
 * This "class" represents a list of processes.
 *--------------------------------------------*/
#ifndef _PROCESS_H
#define _PROCESS_H

#include <pthread.h>

/*---------------------------------------------
 * process
 *
 * input: fd for the process' std input.
 * output: fd for the process' std output.
 * pid: The process' PID.
 * tag: The process' tag.
 * output_thread: Handle for the thread monitoring
 * the process' output.
 * wait_thread: Handle for the thread waiting for
 * the process to finish.
 * prev: Pointer to the next process in the list.
 * next: Pointer to the previous process in the list.
 *--------------------------------------------*/
struct process {
	int input, output, pid;
	char *tag;
	pthread_t output_thread, wait_thread;
	struct process *prev, *next;
};

/*---------------------------------------------
 * init_process_list()
 * Initialize the list of processes.
 *--------------------------------------------*/
void init_process_list(void);

/*---------------------------------------------
 * add_process()
 * Add a process to the process list.
 *
 * p The process to add to the list.
 *--------------------------------------------*/
void add_process(struct process *p);

/*---------------------------------------------
 * remove_process()
 * Remove a process from the process list.
 *
 * p The process to remove from the list. 
 *--------------------------------------------*/
void remove_process(struct process *p);


/*---------------------------------------------
 * find_process_by_tag()
 * Retrieve a process from the process list by tag.
 *
 * tag String identifying the requested process.
 *
 * Return the matching process, if one exists; 
 * NULL, otherwise.
 *--------------------------------------------*/
struct process *find_process_by_tag(char *tag);

/*---------------------------------------------
 * print_process()
 * Print a particular process to the screen.
 *
 * p The process to print.
 *--------------------------------------------*/
void print_process(struct process *p);

/*---------------------------------------------
 * print_processes()
 * Print the entire list of processes to the screen.
 *--------------------------------------------*/
void print_processes(void);

/*---------------------------------------------
 * free_process()
 * Free a process' memory. This is different than
 * remove_process().
 *
 * p The process whose memory is to be freed.
 *--------------------------------------------*/
void free_process(struct process *p);

#endif
