#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>

#include "encode.h"

int main() {
	char *output;

	output = generate_connection_key();

	printf("output: %c%c%c%c%c%c%c%c\n", output[0], 
		output[1],
		output[2],
		output[3],
		output[4],
		output[5],
		output[6],
		output[7]);

	if (!check_connection_key(output, output)) {
		printf("failed! to pass equality!\n");
	}
	if (check_connection_key("00000000", output)) {
		printf("failed! to fail non-equality!\n");
	}
	return 0;
}
