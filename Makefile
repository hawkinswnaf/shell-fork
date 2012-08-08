ifndef CC
CC=gcc -g #-Wall
endif

ifeq ($(OS), android)
LPTHREAD=
else
LPTHREAD=-lpthread
endif

fork: fork.c process.c process.h common.h Makefile
	$(CC) -o fork fork.c process.c $(LPTHREAD)
process_test: process.c process.h process_test.c common.h
	$(CC) -o process_test process_test.c process.c -lpthread
clean:
	rm -f *.o fork process_test core a.out
