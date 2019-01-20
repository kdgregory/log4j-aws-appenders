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
change how the appender creates and configures its service client.

## Client Creation

The appender tries each of the following creation mechanisms, in order:

1. If you specified the `clientFactory` configuration parameter, this appender will attempt to
   invoke it via reflection. This parameter is the fully-qualified name (eg,
   `com.example.mycompany.AWSClientFactory.createKinesisClient`) of a static method that will
   return the client. This is primarily useful if you need to provide explicit credentials to
   the client.
2. If you're using an SDK that supports client factories, the appender will invoke the correct
   factory method via reflection. These methods in turn use a series of mechanisms to find the
   correct credentials and region.
3. If you're using an older SDK, the appender will invoke the default client constructor.


## Endpoint Configuration

The client constructors are hardcoded to use the `us-east-1` region; if you run in a different
region you will need to change that. The appenders try the following, in order:

1. If the `clientEndpoint` configuration parameter is defined, the appender calls `setEndpoint()`
   with that value. Endpoints for CloudWatch Logs are defined
   [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#cwl_region), Kinesis
   [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#ak_region), and SNS
   [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#sns_region). Setting the
   endpoint is the best option for an older SDK, because regions are hardcoded into the SDK.
2. If the `clientRegion` configuration parameter is defined, the appender calls `setRegion()
   with that value. Note that this uses a lookup against a predefined region enum, so may
   only be used with regions explicitly supported by your AWS SDK version.
3. If the `AWS_REGION` environment variable is defined, the appender calls `setRegion()`
   with that value. Note that this uses a lookup against a predefined region enum, so may
   only be used with regions explicitly supported by your AWS SDK version.

The `clientRegion` configuration parameter may also be used with service clients created via
the SDK factory methods. This is only necessary if you want to direct logging output to a
different region than the one where you're running: by default the client builders use a
region provider to get the current region.
