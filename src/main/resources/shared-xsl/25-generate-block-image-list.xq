declare default element namespace "http://www.oxygenxml.com/ns/report";
declare namespace saxon="http://saxon.sf.net/";
declare option saxon:output "method=text";

let $doc := .
let $result := 
	for $image in $doc/report/incident/description
	return $image
return string-join($result, "&#xA;")