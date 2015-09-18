@echo off

pushd %0\..\..

set /p VERSION=<VERSION
set TESLOGGER_DB_URL=%TESLOGGER_DB_URL%

java -cp dist\teslogger-db-inspection-%VERSION%.jar;"lib\*" clojure.main -m teslogger.db-inspection.main

pause
