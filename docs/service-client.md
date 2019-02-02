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

### Older SDKs

The service-client constructors always default to the `us-east-1` region. If you're running with
and older SDK and want to change the region that the appenders use for logging, you have three
options (these are tried in order):

1. Define the `clientEndpoint` configuration parameter. This specifies an explicit hostname,
   and is the preferred approach due to the limitations of specifying a region. Endpoints for
   CloudWatch Logs are listed [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#cwl_region),
   Kinesis [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#ak_region),
   and SNS [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#sns_region).
2. Define the `clientRegion` configuration parameter. Beware, however, that supported
   regions are hardcoded into the SDK, and most of the current regions didn't exist
   when the early 1.11.x SDKs were released.
3. Define the `AWS_REGION` environment variable. This has the same limitations as the
   `clientRegion` configuration parameter.

### Newer SDKs

The AWS "builder" classes use a [region provider chain](https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/regions/AwsRegionProviderChain.html)
to identify the service endpoint they should use: this chain will look at environment variables,
user configuration, and (for deployments on EC2 or Lambda) the region where the application is
running.

While this is sufficient for most applications, you may want to centralize your logging in a
specific region. To support this, you can use the `clientRegion` configuration parameter.
Beware, however, that not all regions may be supported by your version of the SDK; you may
need to upgrade.

The `clientEndpoint` configuration parameter is not supported for builder-created service
clients because endpoint configuration of those clients requires an additional "signing
region" parameter.
