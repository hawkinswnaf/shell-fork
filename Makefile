GCC=gcc -g #-Wall

fork: fork.c process.c process.h common.h Makefile
	$(GCC) -o fork fork.c process.c -lpthread
process_test: process.c process.h process_test.c common.h
	$(GCC) -o process_test process_test.c process.c -lpthread
clean:
	rm -f *.o fork process_test core a.out
