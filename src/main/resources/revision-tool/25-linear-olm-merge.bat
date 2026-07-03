@echo off

set MAPFILE=topics\linear-merge.ditamap
set TMPFILE=temp\dita_list.txt

dir /s /b topics\*.dita | sort > "%TMPFILE%"

(
echo ^<?xml version="1.0" encoding="UTF-8"?^>
echo ^<!DOCTYPE map PUBLIC "-//OASIS//DTD DITA Map//EN" "technicalContent/dtd/map.dtd" []^>
echo ^<?path2rootmap-uri ./?^>
echo ^<map xmlns:ditaarch="http://dita.oasis-open.org/architecture/2005/" cascade="merge" xml:lang="en-US"^>
echo     ^<title^>All Topics^</title^>
) > "%MAPFILE%"

for /f "usebackq delims=" %%F in ("%TMPFILE%") do (
    echo     ^<topicref href="%%~nxF"/^>>> "%MAPFILE%"
)
(
echo ^</map^>
) >> "%MAPFILE%"

del "%TMPFILE%"

call 17_merge.bat
pause