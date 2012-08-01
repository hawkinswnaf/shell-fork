#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "process.h"

int main() {
	struct process a, b, c, d;

	init_process_list();

	memset(&a, 0, sizeof(a));
	memset(&b, 0, sizeof(b));
	memset(&c, 0, sizeof(c));
	memset(&d, 0, sizeof(d));

	a.tag = "a";
	a.pid = 1;
	b.tag = "b";
	b.pid = 2;
	c.tag = "c";
	c.pid = 3;
	d.tag = "d";
	d.pid = 4;



	add_process(&a);
	add_process(&b);
	add_process(&c);
	add_process(&d);
	print_processes();
	printf("------\n");
	
	remove_process(&a);
	print_processes();
	printf("------\n");

	remove_process(&c);
	print_processes();
	printf("------\n");

	remove_process(&b);
	print_processes();
	printf("------\n");
	
	remove_process(&d);
	print_processes();
	printf("------\n");
	return 1;
}
