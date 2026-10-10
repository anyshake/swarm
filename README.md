# Swarm

Swarm is a Java application designed to display and analyze seismic waveforms in real-time. Swarm can connect to and read from a variety of different static and dynamics data sources, including Earthworm & Winston wave servers, SeedLink servers, FDSN Web Services, and wave files. Swarm has both time- and frequency-domain analysis tools, along with a mapping platform. A full-screen kiosk mode allows users to monitor incoming wave and helicorder data. 

See http://volcanoes.usgs.gov/software/swarm.

Swarm was originally developed by Dan Cervelli and Peter Cervelli for the USGS Volcano Science Center (VSC).  More recent maintenance and enhancements were made by Tom Parker (USGS VSC AVO) and Diana Norgaard (USGS VSC VDAP), with contributions from Loren Antolik (USGS VSC HVO), Kevin Frechette (ISTI), Anthony Lomax, Dave Ketchum, Chirag Patel, and Ivan Henson. 

The main repository for Swarm is now at https://gitlab.com/seismic-software/swarm.  The main repository for Swarm was previously at https://code.usgs.gov/vsc/swarm. The code is open source, freely available, and in the public domain.

https://doi.org/10.5066/P93A9MWK

# Build
To build swarm maven 3.8.x is required. The build fails with maven 3.9.9 or later.

mvn verify

# User Guide
[Swarm_User_Guide.pdf](https://gitlab.com/seismic-software/swarm/-/raw/master/docs/Swarm_User_Guide.pdf)
