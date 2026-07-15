@echo off

set SAXON=%~dp0
set CLASSPATH=%SAXON%lib\saxon-he-12.4.jar;%CLASSPATH%
set CLASSPATH=%SAXON%lib\xmlresolver-5.2.2.jar;%CLASSPATH% 

rem ===============================================
rem dummy.xml 재생성
rem ===============================================
cd /d "%~dp0"

if exist "xsl\dummy.xml" del /f /q "xsl\dummy.xml"

echo ^<?xml version="1.0" encoding="UTF-8"?^> > "xsl\dummy.xml"
echo ^<dummy/^> >> "xsl\dummy.xml"

rem ===============================================
rem 실제 배치 돌아가는 구간
rem ===============================================
echo Please wait a moment!
echo Processing... 

rem bookmap.xml 기준으로 chapter/*.xml을 읽어 하나의 map으로 병합
java net.sf.saxon.Transform 							-s:xsl\bookmap.xml  						-o:temp\0200-chapter-merged.xml  			-xsl:xsl\0200-chapter-merge.xsl
rem 병합된 map의 topicref 내용을 topics/*.dita 파일로 분리 생성
java net.sf.saxon.Transform 							-s:temp\0200-chapter-merged.xml 			-o:xsl\dummy.xml  								-xsl:xsl\0210-topicalize.xsl
rem 병합된 chapter 구조에서 topicref 중심의 임시 map 생성
java net.sf.saxon.Transform 							-s:temp\0200-chapter-merged.xml 			-o:temp\tempmap.xml  						-xsl:xsl\0220-make-map.xsl
rem mapname 기준으로 KIA metadata와 표지용 빈 메타 항목 삽입
java net.sf.saxon.Transform 							-s:temp\tempmap.xml  					-o:temp\0250-metadata_Insert.xml 				-xsl:xsl\0250_chapter_metadata_Insert.xsl
rem topics/*.ditamap 파일 생성 및 topicmeta/outputclass/들여쓰기 정리
java net.sf.saxon.Transform 							-s:temp\0250-metadata_Insert.xml			-o:xsl\dummy.xml								-xsl:xsl\0230-map-beautify_meta.xsl
rem topics/*.dita 파일에 topic 타입별 DOCTYPE을 붙이고 출력 정리
java net.sf.saxon.Transform -catalog:xsl\catalog.xml	-s:temp\0250-metadata_Insert.xml			-o:xsl\dummy.xml  								-xsl:xsl\0240-dita-beautify.xsl


rd /q/s chapter
rem rd /q/s temp
echo Done.
pause