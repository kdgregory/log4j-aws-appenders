This page lists the IAM permissions and SDK dependencies required for each destination
or feature.


## Destination: CloudWatch Logs

Permissions:

* `logs:CreateLogGroup`
* `logs:CreateLogStream`
* `logs:DescribeLogGroups`
* `logs:DescribeLogStreams`
* `logs:PutLogEvents`
* `logs:PutRetentionPolicy`

Dependencies:

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-logs</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>cloudwatchlogs</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```


## Destination: Kinesis Stream

Permissions, base:

* `kinesis:DescribeStreamSummary`
* `kinesis:ListStreams`
* `kinesis:PutRecords`

Permissions, if auto-create enabled:

* `kinesis:CreateStream`
* `kinesis:IncreaseStreamRetentionPeriod`

Dependencies:

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-kinesis</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>kinesis</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```


## Destination: SNS Topic

Permissions, base:

* `sns:ListTopics`
* `sns:Publish`

Permissions, if auto-create enabled:

* `sns:CreateTopic`

Dependencies:

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-sns</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sns</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```


## Feature: Assumed Role

Permissions:

* `iam:ListRoles`
* `sts:AssumeRole`

Dependencies:

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-iam</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-sts</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>iam</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sts</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```


## Feature: Current Account ID substitution

Permissions:

* _None_


Dependencies (these overlap with Assumed Role dependencies):

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-sts</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sts</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```


## Feature: EC2 Instance Tags substitution

Permissions:

* `ec2:DescribeTags`

Dependencies:

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-ec2</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>ec2</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```


## Feature: SSM Parameter Store substitution

Permissions:

* `ssm:GetParameter`

Dependencies:

* SDK version 1

  ```
  <dependency>
      <groupId>com.amazonaws</groupId>
      <artifactId>aws-java-sdk-ssm</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```

* SDK version 2:

  ```
  <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>ssm</artifactId>
      <version>${aws-sdk.version}</version>
  </dependency>
  ```
