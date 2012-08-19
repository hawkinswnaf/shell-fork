#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>

static void encode_triple(uint8_t triple[3], char encoded[4]) {
	static char encode_table[] = {'A', 'B', 'C', 'D', 'E',
		'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
		'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y',
		'Z', 'a', 'b', 'c', 'd', 'e',
		'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
		'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
		'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
		'9', '+', '/'};

	uint8_t preencoded_triple[4] = {0,};
	char encoded_triple[4] = {'0', };

	preencoded_triple[0] = triple[0] >> 2;
	preencoded_triple[1] = ((triple[0] & 0x03) << 4) + (triple[1] >> 4);
	preencoded_triple[2] = ((triple[1] & 0x0F) << 2) + ((triple[2] & 0xC0) >> 6);
	preencoded_triple[3] = triple[2] & 0x3F;

	encoded[0] = encode_table[preencoded_triple[0]];
	encoded[1] = encode_table[preencoded_triple[1]];
	encoded[2] = encode_table[preencoded_triple[2]];
	encoded[3] = encode_table[preencoded_triple[3]];

}

inline int check_connection_key(char a[8], char b[8]) {
	return !memcmp(a, b, 8);
}

char *generate_connection_key() {
	int dev_random = 0;

	uint8_t triples[6] = {0,};
	char *output = (char*)malloc(sizeof(char)*8);

	memset(output, 0, sizeof(char)*8);

	if (0 > (dev_random = open("/dev/random", O_RDONLY))) {
		printf("Error opening /dev/random!\n");
		return;
	}
	if (6 != read(dev_random, (void*)&triples, 6)) {
		printf("Couldn't fill up the random triples!\n");
		goto out;
	}

	encode_triple(triples, output);	
	encode_triple(triples + 3, output + 4);	
#if 0
	printf("input: %d:%d:%d:%d:%d:%d\n", triples[0], 
		triples[1],
		triples[2],
		triples[3],
		triples[4],
		triples[5]);

	printf("output: %c%c%c%c%c%c%c%c\n", output[0], 
		output[1],
		output[2],
		output[3],
		output[4],
		output[5],
		output[6],
		output[7]);
#endif 
out:
	close(dev_random);
	return output;
}
