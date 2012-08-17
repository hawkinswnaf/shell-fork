ifeq ($(OS), android)
include Makefile.android
LPTHREAD=
else
CC=gcc -g #-Wall
LPTHREAD=-lpthread
endif

fork: fork.c process.c process.h common.h Makefile
	$(CC) $(CFLAGS) $(CPPFLAGS) -DPOSIX_MISTAKE -c fork.c process.c $(LPTHREAD)
	$(CC) $(LDFLAGS) -o fork fork.o process.o $(LPTHREAD)
process_test: process.c process.h process_test.c common.h
	$(CC) -o process_test process_test.c process.c -lpthread
clean:
	rm -f *.o fork process_test core a.out
