# Service Client Configuration

In order to support all AWS releases in the 1.11.x sequence, the appenders must support
the default client constructors. However, these constructors have a few limitations:

* While they use a [credentials provider chain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html)
  that looks for client credentials in multiple locations, they don't do the same for region.
  Instead, they default to the `us-east-1` region and expect applications to change the client
  after construction.
* Some use cases require providing credentials other than those that the will be picked up by
  the default provider chain. For example, you might want to log to a stream or topic owned
  by a different AWS account.

To work around these limitations, the appenders give you several configuration parameters that
change how the appender creates its service client. The appender tries each of the following
creation mechanisms, in order:

1. If you specified the `clientFactory` configuration parameter, this appender will attempt to
   invoke it via reflection. This parameter is the fully-qualified name (eg,
   `com.example.mycompany.AWSClientFactory.createKinesisClient`) of a static method that will
   return the client. This is primarily useful if you need to provide explicit credentials to
   the client.
2. If you're using an SDK that supports client factories, the appender will invoke the correct
   `defaultClient()` method via reflection. These methods in turn use a series of mechanisms
   to find the correct credentials and region.
3. If you're using an older SDK, the appender will invoke the default client constructor.

If you're stuck with the third case, but are not running in the `us-east-1` region, you have
two further options:

* You can specify the client endpoint using the `clientEndpoint` configuration parameter;
  see the [AWS docs](https://docs.aws.amazon.com/general/latest/gr/rande.html) for a list
  of endpoint names. This parameter is primarily intended for applications that must use
  an older AWS SDK version but want to log outside the `us-east-1` region. For example, to
  direct Kinesis logging to a stream in the `us-west-1` region:

  ```
  log4j.appender.kinesis.clientEndpoint=kinesis.us-west-1.amazonaws.com
  ```
* Provide the `AWS_REGION` environment variable. However, be aware that older SDKs do not
  recognize all current regions, so may fail to initialize.
