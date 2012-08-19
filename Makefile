ifeq ($(OS), android)
include Makefile.android
LPTHREAD=
else
CC=gcc -g #-Wall
LPTHREAD=-lpthread
endif

fork: fork.c process.c process.h common.h encode.h encode.c Makefile
	$(CC) $(CFLAGS) $(CPPFLAGS) -DPOSIX_MISTAKE -c encode.c fork.c process.c $(LPTHREAD)
	$(CC) $(LDFLAGS) -o fork fork.o process.o encode.o $(LPTHREAD)
process_test: process.c process.h process_test.c common.h
	$(CC) -o process_test process_test.c process.c -lpthread
encode_test: encode.c encode.h encode_test.c common.h
	$(CC) -o encode_test encode_test.c encode.c -lpthread
clean:
	rm -f *.o fork process_test core a.out
