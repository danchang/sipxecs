Name:           sec
Version:        2.6.2
Release:        1%{?dist}
Summary:        Simple Event Correlator script to filter log file entries
Group:          System Environment/Daemons
License:        GPLv2+
URL:            http://simple-evcorr.sourceforge.net/
Source0:        http://downloads.sourceforge.net/simple-evcorr/%{name}-%{version}.tar.gz
Source1:        sec.service
Source3:        sec.logrotate
# Example files and configuration info
Source4:        conf.README
Source5:        http://simple-evcorr.sourceforge.net/rulesets/amavisd.sec
Source6:        http://simple-evcorr.sourceforge.net/rulesets/bsd-MONITOR.sec
Source7:        http://simple-evcorr.sourceforge.net/rulesets/bsd-PHYSMOD.sec
Source8:        http://simple-evcorr.sourceforge.net/rulesets/bsd-USERACT.sec
Source9:        http://simple-evcorr.sourceforge.net/rulesets/bsd-general.sec
Source10:       http://simple-evcorr.sourceforge.net/rulesets/bsd-mpd.sec
Source11:       http://simple-evcorr.sourceforge.net/rulesets/cisco-syslog.sec
Source12:       http://simple-evcorr.sourceforge.net/rulesets/cvs.sec
Source13:       http://simple-evcorr.sourceforge.net/rulesets/dameware.sec
Source14:       http://simple-evcorr.sourceforge.net/rulesets/hp-openview.sec
Source15:       http://simple-evcorr.sourceforge.net/rulesets/labrea.sec
Source16:       http://simple-evcorr.sourceforge.net/rulesets/pix-general.sec
Source17:       http://simple-evcorr.sourceforge.net/rulesets/pix-security.sec
Source18:       http://simple-evcorr.sourceforge.net/rulesets/pix-url.sec
Source19:       http://simple-evcorr.sourceforge.net/rulesets/portscan.sec
Source20:       http://simple-evcorr.sourceforge.net/rulesets/snort.sec
Source21:       http://simple-evcorr.sourceforge.net/rulesets/snortsam.sec
Source22:       http://simple-evcorr.sourceforge.net/rulesets/ssh-brute.sec
Source23:       http://simple-evcorr.sourceforge.net/rulesets/ssh.sec
Source24:       http://simple-evcorr.sourceforge.net/rulesets/vtund.sec
Source25:       http://simple-evcorr.sourceforge.net/rulesets/windows.sec
BuildArch:      noarch

Requires:       logrotate

%if  0%{?fedora} >= 15
Requires(post):   systemd-units
Requires(preun):  systemd-units
Requires(postun): systemd-units
%endif

%if  0%{?fedora} >= 15
BuildRequires: systemd-units
%endif

%description
SEC is a simple event correlation tool that reads lines from files, named
pipes, or standard input, and matches the lines with regular expressions,
Perl subroutines, and other patterns for recognizing input events.
Events are then correlated according to the rules in configuration files,
producing output events by executing user-specified shell commands, by
writing messages to pipes or files, etc.

%prep
%setup -q

%build

%install
# Install SEC and its associated files
install -D -m 0755 -p sec        %{buildroot}%{_bindir}/sec
install -D -m 0644 -p sec.man    %{buildroot}%{_mandir}/man1/sec.1
install -D -m 0644 -p %{SOURCE1} %{buildroot}%{_unitdir}/sec.service
install -D -m 0644 -p %{SOURCE3} %{buildroot}%{_sysconfdir}/logrotate.d/sec

# Install the example config files and readme
install -D -m 0644 -p %{SOURCE4} %{buildroot}%{_sysconfdir}/%{name}/README
install -d -m 0755  examples
install -m 0644 -p %{SOURCE5} %{SOURCE6} %{SOURCE7} %{SOURCE8} \
                   %{SOURCE9} %{SOURCE10} %{SOURCE11} %{SOURCE12} \
                   %{SOURCE13} %{SOURCE14} %{SOURCE15} %{SOURCE16} \
                   %{SOURCE17} %{SOURCE18} %{SOURCE19} %{SOURCE20} \
                   %{SOURCE21} %{SOURCE22} %{SOURCE23} %{SOURCE24} \
                   %{SOURCE25} examples/

# Remove executable bits because these files get packed as docs
chmod 0644 contrib/convert.pl contrib/swatch2sec.pl

%post
if [ $1 -eq 1 ]; then
        # Initial installation
        /bin/systemctl daemon-reload >/dev/null 2>&1 || :
fi

%preun
if [ $1 -eq 0 ]; then
        # Package removal, not upgrade
        /bin/systemctl --no-reload disable sec.service > /dev/null 2>&1 || :
        /bin/systemctl stop sec.service > /dev/null 2>&1 || :
fi

%postun
/bin/systemctl daemon-reload >/dev/null 2>&1 || :
if [ $1 -ge 1 ]; then
        # Package upgrade, not uninstall
        /bin/systemctl try-restart sec.service >/dev/null 2>&1 || :
fi

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
%doc ChangeLog COPYING README contrib/convert.pl contrib/itostream.c contrib/swatch2sec.pl examples
%config(noreplace) %{_sysconfdir}/%{name}
%config(noreplace) %{_sysconfdir}/logrotate.d/sec
%{_bindir}/sec
%{_mandir}/man1/sec.1*
%{_unitdir}/sec.service

%changelog
* Sat Feb 25 2012 Douglas <dhubler@ezuce.com> - 2.6.2-1
- Compile on centos 6 systemd-units

* Sun Feb 5 2012 Stefan Schulze Frielinghaus <stefansf@fedoraproject.org> - 2.6.2-0
- New upstream release

* Sat Jan 14 2012 Fedora Release Engineering <rel-eng@lists.fedoraproject.org> - 2.6.1-1
- Rebuilt for https://fedoraproject.org/wiki/Fedora_17_Mass_Rebuild

* Mon Sep 19 2011 Stefan Schulze Frielinghaus <stefansf@fedoraproject.org> - 2.6.1-0
- New upstream release

* Sat Jun 11 2011 Stefan Schulze Frielinghaus <stefansf@fedoraproject.org> - 2.6.0-2
- Upgrade to systemd

* Sun Mar 20 2011 Stefan Schulze Frielinghaus <stefansf@fedoraproject.org> - 2.6.0-1
- New upstream release

* Wed Feb 09 2011 Fedora Release Engineering <rel-eng@lists.fedoraproject.org> - 2.5.3-1
- Rebuilt for https://fedoraproject.org/wiki/Fedora_15_Mass_Rebuild

* Thu Dec 10 2009 Stefan Schulze Frielinghaus <stefan@seekline.net> - 2.5.3-0
- New upstream release

* Tue Sep 29 2009 Stefan Schulze Frielinghaus <stefan@seekline.net> - 2.5.2-1
- New upstream release
- SPEC file cleanup
- Init script cleanup
- Removed some examples because of licensing issues. Upstream has clarified
  and changed most of the license tags to GPLv2. Additionally, upstream
  will include the examples in the next release.
- Removed a provide statement since a period was in the name and no other
  package required that special name.

* Sun Jul 26 2009 Fedora Release Engineering <rel-eng@lists.fedoraproject.org> - 2.4.1-4
- Rebuilt for https://fedoraproject.org/wiki/Fedora_12_Mass_Rebuild

* Wed Feb 25 2009 Fedora Release Engineering <rel-eng@lists.fedoraproject.org> - 2.4.1-3
- Rebuilt for https://fedoraproject.org/wiki/Fedora_11_Mass_Rebuild

* Thu Sep  4 2008 Tom "spot" Callaway <tcallawa@redhat.com> - 2.4.1-2
- fix license tag

* Mon May 28 2007 Chris Petersen <rpm@forevermore.net>                  2.4.1-1
- Update to 2.4.1

* Wed Dec 06 2006 Chris Petersen <rpm@forevermore.net>                  2.4.0-1
- Update to 2.4.0

* Mon Jun 12 2006 Chris Petersen <rpm@forevermore.net>                  2.3.3-4
- Change group to keep rpmlint happy
- Fix permissions on the logrotate script

* Thu Jun 08 2006 Chris Petersen <rpm@forevermore.net>                  2.3.3-3
- Clean up spec
- Add ghost file entries for the default logfile and pid
- Add logrotate script
- Add more bleedingsnort examples
- Add pid to sec.sysconfig and completely rewrite to handle multiple instances
- Fix download URL
- Fix echo log command in 001_init.sec
- Rewrite sysV init script to handle multiple instances (based loosely on vsftpd)

* Mon May 01 2006 Didier Moens <Didier.Moens@dmbr.UGent.be>             2.3.3-2
- Change init script to not start by default in any runlevel

* Fri Apr 28 2006 Didier Moens <Didier.Moens@dmbr.UGent.be>             2.3.3-1
- Upgrade to upstream 2.3.3
- Add status to init script

* Thu Sep 22 2005 Didier Moens <Didier.Moens@dmbr.UGent.be>             2.3.2-4
- Update Source locations

* Thu Sep 22 2005 Didier Moens <Didier.Moens@dmbr.UGent.be>             2.3.2-3
- Change permissions on /usr/bin/sec

* Thu Sep 22 2005 Didier Moens <Didier.Moens@dmbr.UGent.be>             2.3.2-2
- Create initial startup rulesets
- Add examples
- Refine init script

* Wed Sep 21 2005 Didier Moens <Didier.Moens@dmbr.UGent.be>             2.3.2-1
- First build
