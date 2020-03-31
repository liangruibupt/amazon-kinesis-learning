# Learning Amazon Kinesis Development

The master branch provides completed code for the [Process Real-Time Stock Data Using KPL and KCL Tutorial][learning-kinesis]  in the [Kinesis Developer Guide](https://docs.aws.amazon.com/streams/latest/dev/tutorial-stock-data-kplkcl2.html).

The tutorial uses KCL to demonstrate how to send a stream of records to Kinesis Data Streams and implement an application that consumes and processes the records in near-real time. 

[learning-kinesis](https://docs.aws.amazon.com/streams/latest/dev/tutorial-stock-data-kplkcl2.html)
[kinesis-developer-guide](http://docs.aws.amazon.com/kinesis/latest/dev/introduction.html)

## Step 1: Create a Data Stream
1. Open the Kinesis console and create a Kinesis Stream with Name: StockTradeStream
2. Accept default setting

## Step 2: Create an IAM Policy and attach to User
IAM Policy
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Stmt123",
      "Effect": "Allow",
      "Action": [
        "kinesis:DescribeStream",
        "kinesis:PutRecord",
        "kinesis:PutRecords",
        "kinesis:GetShardIterator",
        "kinesis:GetRecords",
        "kinesis:ListShards",
        "kinesis:DescribeStreamSummary",
        "kinesis:RegisterStreamConsumer"
      ],
      "Resource": [
        "*"
      ]
    },
    {
      "Sid": "Stmt234",
      "Effect": "Allow",
      "Action": [
        "kinesis:SubscribeToShard",
        "kinesis:DescribeStreamConsumer"
      ],
      "Resource": [
        "*"
      ]
    },
    {
      "Sid": "Stmt456",
      "Effect": "Allow",
      "Action": [
        "dynamodb:*"
      ],
      "Resource": [
        "*"
      ]
    },
    {
      "Sid": "Stmt789",
      "Effect": "Allow",
      "Action": [
        "cloudwatch:PutMetricData"
      ],
      "Resource": [
        "*"
      ]
    }
  ]
}
```

## Step 3: Download and Build the Code
```bash
git clone git@github.com:aws-samples/amazon-kinesis-learning.git
git clone https://github.com/aws-samples/amazon-kinesis-learning.git
```
Add the dependency to project, you can edit the pom.xml
```xml
<properties>
        <aws-kinesis-client.version>2.2.9</aws-kinesis-client.version>
        <aws-java-sdk.version>1.11.754</aws-java-sdk.version>
        <aws-sdk-client.version>2.11.4</aws-sdk-client.version>
        <jackson.version>2.10.3</jackson.version>
    </properties>


    <dependencies>
        <dependency>
            <groupId>software.amazon.kinesis</groupId>
            <artifactId>amazon-kinesis-client</artifactId>
            <version>${aws-kinesis-client.version}</version>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>apache-client</artifactId>
            <version>${aws-sdk-client.version}</version>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>netty-nio-client</artifactId>
            <version>${aws-sdk-client.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>kinesis</artifactId>
            <version>${aws-sdk-client.version}</version>
        </dependency>
        <dependency>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
            <version>1.2</version>
        </dependency>
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk-core</artifactId>
            <version>${aws-java-sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk-kinesis</artifactId>
            <version>${aws-java-sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>com.amazonaws</groupId>
            <artifactId>aws-java-sdk</artifactId>
            <version>${aws-java-sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-cbor</artifactId>
            <version>${jackson.version}</version>
        </dependency>
    </dependencies>
```


## Step 4: Implement the Producer
1. Edit StockTradesWriter.java
```java

private static void sendStockTrade(StockTrade trade, KinesisAsyncClient kinesisClient,
            String streamName) {
        byte[] bytes = trade.toJsonAsBytes();
        // The bytes could be null if there is an issue with the JSON serialization by the Jackson JSON library.
        if (bytes == null) {
            LOG.warn("Could not get JSON bytes for stock trade");
            return;
        }

        LOG.info("Putting trade: " + trade.toString());
        PutRecordRequest request = PutRecordRequest.builder()
                .partitionKey(trade.getTickerSymbol()) // We use the ticker symbol as the partition key, explained in the Supplemental Information section below.
                .streamName(streamName)
                .data(SdkBytes.fromByteArray(bytes))
                .build();
        try {
            kinesisClient.putRecord(request).get();
        } catch (InterruptedException e) {
            LOG.info("Interrupted, assuming shutdown.");
        } catch (ExecutionException e) {
            LOG.error("Exception while sending data to Kinesis. Will try again next cycle.", e);
        }
    }
                    
```

2. To run the producer
```bash
java com.amazonaws.services.kinesis.samples.stocktrades.writer.StockTradesWriter StockTradeStream cn-northwest-1 
```

## Step 5: Implement the Consumer
1. Edit StockTradeRecordProcessor.java
```java
@Override
    public void processRecords(ProcessRecordsInput processRecordsInput) {
        List<KinesisClientRecord> records = processRecordsInput.records();
        // Used to update the last processed record
        RecordProcessorCheckpointer checkpointer = processRecordsInput.checkpointer();
        for (KinesisClientRecord recode : records) {
            try {
                processRecord(recode);
            } catch (Exception ex) {
                log.warn(ex.getMessage());
            }
        }
        reportStats();

    }

    private void reportStats() {
        System.out.println("****** Shard " + kinesisShardId + " stats for last 1 minute ******\n" + stockStats + "\n"
                + "****************************************************************\n");

    }

    private void resetStats() {

        stockStats = new StockStats();

    }

    private void processRecord(KinesisClientRecord record) {
        byte[] arr = new byte[record.data().remaining()];
        record.data().get(arr);
        StockTrade trade = StockTrade.fromJsonAsBytes(arr);
        if (trade == null) {
            log.warn(
                    "Skipping record. Unable to parse record into StockTrade. Partition Key: " + record.partitionKey());
            return;
        }
        log.info("processRecord from kinesis." + trade);
        stockStats.addStockTrade(trade);
        // v1.x
        // StockTrade trade = StockTrade.fromJsonAsBytes(record.getData().array());
        // if (trade == null) {
        // LOG.warn("Skipping record. Unable to parse record into StockTrade. Partition
        // Key: "
        // + record.getPartitionKey());
        // return;
        // }
        // stockStats.addStockTrade(trade);
    }    

```

2. Edit StockStats.java
```java
    // Ticker symbol of the stock that had the largest quantity of shares sold
    private String largestSellOrderStock;
    // Quantity of shares for the largest sell order trade
    private long largestSellOrderQuantity;

    /**
     * Updates the statistics taking into account the new stock trade received.
     *
     * @param trade Stock trade instance
     */
    public void addStockTrade(StockTrade trade) {
        // update buy/sell count
        TradeType type = trade.getTradeType();
        Map<String, Long> counts = countsByTradeType.get(type);
        Long count = counts.get(trade.getTickerSymbol());
        if (count == null) {
            count = 0L;
        }
        counts.put(trade.getTickerSymbol(), ++count);

        // update most popular stock
        String mostPopular = mostPopularByTradeType.get(type);
        if (mostPopular == null || countsByTradeType.get(type).get(mostPopular) < count) {
            mostPopularByTradeType.put(type, trade.getTickerSymbol());
        }

        if (type == TradeType.SELL) {
            if (largestSellOrderStock == null || trade.getQuantity() > largestSellOrderQuantity) {
                largestSellOrderStock = trade.getTickerSymbol();
                largestSellOrderQuantity = trade.getQuantity();
            }
        }

    }

    public String toString() {
        return String.format(
                "Most popular stock being bought: %s, %d buys.%n" + "Most popular stock being sold: %s, %d sells.",
                getMostPopularStock(TradeType.BUY), getMostPopularStockCount(TradeType.BUY),
                getMostPopularStock(TradeType.SELL), getMostPopularStockCount(TradeType.SELL), largestSellOrderQuantity,
                largestSellOrderStock);
    }
```

3. To run the consumer

```bash
java com.amazonaws.services.kinesis.samples.stocktrades.processor.StockTradesProcessor StockTradesProcessor StockTradeStream cn-northwest-1
```

## Step 6: Cleanup Up
1. Shut down any producers and consumers that you may still have running.

2. Open the Kinesis console and Choose the stream that you created **StockTradeStream**.

3. Delete Stream.

4. Open the DynamoDB console and Delete the **StockTradesProcessor** table.


## Proxy support kinesis-client
Based on below documents, aws-sdk-java 2.0+ Sync Apache HTTP Client and 2.0+ Async Netty HTTP Client can support Client HTTP Proxy Configuration

https://github.com/aws/aws-sdk-java-v2/blob/master/docs/LaunchChangelog.md#132-client-http-proxy-configuration

https://github.com/aws/aws-sdk-java-v2/issues/858

```java
ProxyConfiguration.Builder proxyConfig =
        ProxyConfiguration.builder().host("http-se.some.host.com").port(8080).username("foo").password("bar");

ApacheHttpClient.Builder httpClientBuilder = 
        ApacheHttpClient.builder()
                        .proxyConfiguration(proxyConfig.build());
KinesisAsyncClient kinesisClient = KinesisAsyncClient.builder().region(region).httpClientBuilder(httpClientBuilder).build();

NettyNioAsyncHttpClient.Builder nettyBuilder = NettyNioAsyncHttpClient.builder()
                .proxyConfiguration(proxyConfig.builder());
KinesisAsyncClient kinesisClient = KinesisAsyncClient.builder().region(region).httpClientBuilder(nettyBuilder).build();
```

## License Summary

This sample code is made available under the MIT-0 license. See the LICENSE file.
