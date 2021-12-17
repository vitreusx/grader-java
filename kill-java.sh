ps aux | grep jb406103 | grep Gradle | awk '{print $2}' | xargs kill -9
