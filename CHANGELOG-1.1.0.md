# Fishing Catch Timer 1.1.0

- Added per-server + dimension calibration profiles with a rolling median of 24 confirmed cue-to-bite samples.
- Added sample count, learned median, and mean absolute prediction error statistics.
- Added fishing-particle signature and wake-direction validation before a cue can start the timer.
- Added a trajectory estimator that measures the closing distance of successive valid wake particles and blends a stable estimate into the countdown.
- Kept the bite-window state visible at zero until the synced bite flag is received or the bobber is reeled in.
- Added a free-position HUD editor with drag placement and normalized X/Y coordinates saved in the config.
- Updated the web configurator with learning, particle-filter, trajectory, HUD-position controls, and a draggable preview.
