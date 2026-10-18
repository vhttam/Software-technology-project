$h2Jar = (Get-ChildItem -Path "$env:USERPROFILE\.m2\repository\com\h2database\h2" -Filter "*.jar" -Recurse | Select-Object -First 1).FullName
javac -cp $h2Jar -d scripts/bin scripts/InvestigateJob.java
java -cp "$h2Jar;scripts/bin" vn.edu.cnpm.runner.InvestigateJob
