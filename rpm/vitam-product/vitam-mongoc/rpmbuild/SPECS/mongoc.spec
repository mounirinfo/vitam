Name:          vitam-mongoc
Version:       4.0
Release:       1%{?dist}
Summary:       Service files for Vitam mongoc cluster configuration nodes
Group:         Applications/Databases
License:       Cecill v2.1
BuildArch:     noarch
URL:           http://www.mongodb.org
Source0:       vitam-mongoc.service
%global        vitam_service_name vitam-mongoc

BuildRequires: systemd-units
Requires:      systemd
Requires:      mongodb-org >= 4.0
Requires:      vitam-user-vitamdb

%description
Service files for Vitam mongoc cluster configuration nodes

%prep

%install
mkdir -p %{buildroot}/%{_unitdir}
cp %{SOURCE0} %{buildroot}/%{_unitdir}/vitam-mongoc.service

%pre

%post
%systemd_post vitam-mongoc.service

%preun
%systemd_preun  vitam-mongoc.service

%postun
%systemd_postun  vitam-mongoc.service

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
%{_unitdir}/vitam-mongoc.service

%doc


%changelog
* Mon Aug 27 2018 Bumped to 4.0.1 mongo version French Prime minister Office/SGMAP/DINSIC/Vitam Programm <contact.vitam@culture.gouv.fr>
* Fri Oct 6 2017 Bumped to 3.4.9 mongo version French Prime minister Office/SGMAP/DINSIC/Vitam Programm <contact.vitam@culture.gouv.fr>
* Tue Oct 11 2016 French Prime minister Office/SGMAP/DINSIC/Vitam Program <contact.vitam@culture.gouv.fr>
- Initial version
