# Jenkins Performance Testing Plugin

This Jenkins plugin adds the ability of running common performance testing
tools and monitoring hardware resources to Jenkins.

As of v1.0.1, Jenkins Performance Testing Plugin supports Jenkins pipeline.

## Usage
Use NMON to monitor hardware resources when running some tasks
```groovy
sh 'mkdir -p output logs'
withMonitoring([
  nmon(dir: 'output', target: sshTarget(user: 'user1', host: 'host1.example.com', password: 'password1')),
  nmon(dir: 'output', target: sshTarget(user: 'user2', host: 'host2.example.com', password: 'password2')),
])
{
  echo 'doing some tasks...'
  sh 'jmeter -Djmeter.save.saveservice.output_format=xml -n -t test-plan-1.jmx -l output/test-result-1.jtl -j logs/test-log-1.log'
  sh 'jmeter -Djmeter.save.saveservice.output_format=xml -n -t test-plan-2.jmx -l output/test-result-2.jtl -j logs/test-log-2.log'
}
performanceReport 

```

Run Jmeter to do a stress test to remote hosts:

```groovy
jmeter file: 'somefile.xml', out: 'somefile.jmx'
```