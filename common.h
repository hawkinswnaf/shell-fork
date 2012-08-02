#ifndef _COMMON_H 
#define _COMMON_H 

/*
 * start of debugging output configuration.
 */
#define DEBUG_LEVEL 3
//#define DEBUG_LEVEL 2
//#define DEBUG_LEVEL 1
//#define DEBUG_LEVEL 0

#if DEBUG_LEVEL >= 3
#define DEBUG_3 printf
#else
#define DEBUG_3  
#endif

#if DEBUG_LEVEL >= 2
#define DEBUG_2 printf
#else
#define DEBUG_2  
#endif 

#if DEBUG_LEVEL >= 1
#define DEBUG_1 printf
#else
#define DEBUG_1  
#endif 

#ifndef DEBUG_LEVEL
#define DEBUG_1  
#define DEBUG_2  
#define DEBUG_3  
#else
#define DEBUG_MODE
#endif

/*
 * end of debugging output configuration.
 */

#define GLOBAL_OUTPUT

#define LINE_BUFFER_SIZE 80

#define READ_END 0
#define WRITE_END 1
#define GLOBAL_PIPE_UNINITIALIZED -1

#endif
