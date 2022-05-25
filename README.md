# kotlin-lab ![Coverage](.github/badges/coverage.svg) ![Branches](.github/badges/branches.svg)
City Transport Simulation

- stations csv contains all stations on route and lat/lng required
- network folder contains mappings of routes between stations
- each unique route per line gets a seperate mapping within line config
- tests test each line end to end
- each transporter per line will travel the calculated distance between stations based on haversine calculation
