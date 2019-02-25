# Jenkins Performance Testing Plugin

This Jenkins plugin adds the ability of running common performance testing
tools and monitoring hardware resources to Jenkins.

As of v1.0.1, Jenkins Performance Testing Plugin supports Jenkins pipeline.

## Usage
Use NMON to monitor hardware resources when running some tasks
```groovy
withMonitoring([
  nmon(dir: 'output-1', target: sshTarget(user: 'user1', host: 'host1.example.com', password: 'password1')),
  nmon(dir: 'output-2', target: sshTarget(user: 'user2', host: 'host2.example.com', password: 'password2')),
])
{
  echo 'doing some tasks...'
  //jmeter files:['1.jmx', '2.jmx', '3.jmx']
  //sh 'sleep 10'
}
```

Run Jmeter to do a stress test to remote hosts:

```groovy
jmeter file: 'somefile.xml', out: 'somefile.jmx'
```