# R8 rules for the wear app.
#
# Intentionally empty for now. Rules are added only when a release build is observed to
# break, and each rule carries a comment explaining which class needed it and why —
# a blanket `-keep class **` defeats the point of shrinking and hides real problems.
#
# Note for later phases: TileService and ComplicationDataSourceService are instantiated by
# the system, not by our code, so they will need explicit keep rules once they exist.
