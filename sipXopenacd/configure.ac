AC_PREREQ(2.57)
AC_INIT(sipXopenacd, 0.0.4.5.2, sipx-dev@list.sipfoundry.org)
AC_CONFIG_AUX_DIR(config)
AC_CONFIG_SRCDIR([src/sipxplugin_app.erl])
m4_include([config/openacd.m4])
m4_include([config/sipXlib2.m4])
AM_INIT_AUTOMAKE(foreign tar-ustar)
SIPX_OPENACD
AC_ERLANG_PATH_ERLC
AC_CONFIG_FILES([
  Makefile
  bin/Makefile
  etc/Makefile
  src/Makefile
])
AC_OUTPUT
