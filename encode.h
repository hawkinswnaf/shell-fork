/*---------------------------------------------
 * Code to generate and check a the fork
 * connection key.
 *--------------------------------------------*/
#ifndef ENCODE_H
#define ENCODE_H

/*---------------------------------------------
 * generate_connection_key()
 * Generate an 8-byte base64-encoded connection
 * key used to authorize client connections.
 *--------------------------------------------*/
char *generate_connection_key();

/*---------------------------------------------
 * check_connection_key()
 * Check whether two connection keys match.
 * 
 * a Connection key.
 * b Connection key.
 *
 * Return 1 or 0 depending upon whether a and 
 * b match or not, respectively.
 *--------------------------------------------*/
inline int check_connection_key(char a[8], char b[8]);
#endif
