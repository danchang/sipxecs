EXTRA_DIST = \
	posix/pico.h \
	posix/RawSocket.h

mydir = @SIPX_JAVADIR@/sipXcommons
my_LTLIBRARIES = \
	libhostname.la \
	librocksaw.la \
	libprocessid.la

AM_CFLAGS = -Wall -pipe -ansi -pthread -fpic

libhostname_la_SOURCES = \
	posix/Hostname.c

librocksaw_la_SOURCES = \
	posix/RawSocket.c

libprocessid_la_SOURCES = \
	posix/ProcessID.c