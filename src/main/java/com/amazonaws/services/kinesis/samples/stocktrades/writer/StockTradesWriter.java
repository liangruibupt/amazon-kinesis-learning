/*
 * Copyright 2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Amazon Software License (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/asl/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.services.kinesis.samples.stocktrades.writer;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.kinesis.samples.stocktrades.model.StockTrade;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.KinesisClientUtil;

/**
 * Continuously sends simulated stock trades to Kinesis
 *
 */
public class StockTradesWriter {

    private static final Log LOG = LogFactory.getLog(StockTradesWriter.class);

    private static void checkUsage(final String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: " + StockTradesWriter.class.getSimpleName() + " <stream name> <region>");
            System.exit(1);
        }
    }

    /**
     * Checks if the stream exists and is active
     *
     * @param kinesisClient Amazon Kinesis client instance
     * @param streamName    Name of stream
     */
    private static void validateStream(final KinesisAsyncClient kinesisClient, final String streamName) {
        try {
            final DescribeStreamRequest describeStreamRequest = DescribeStreamRequest.builder().streamName(streamName)
                    .build();
            final DescribeStreamResponse describeStreamResponse = kinesisClient.describeStream(describeStreamRequest)
                    .get();
            if (!describeStreamResponse.streamDescription().streamStatus().toString().equals("ACTIVE")) {
                System.err.println("Stream " + streamName + " is not active. Please wait a few moments and try again.");
                System.exit(1);
            }
        } catch (final Exception e) {
            System.err.println("Error found while describing the stream " + streamName);
            System.err.println(e);
            System.exit(1);
        }
    }

    /**
     * Uses the Kinesis client to send the stock trade to the given stream.
     *
     * @param trade         instance representing the stock trade
     * @param kinesisClient Amazon Kinesis client
     * @param streamName    Name of stream
     */
    private static void sendStockTrade(final StockTrade trade, final KinesisAsyncClient kinesisClient,
            final String streamName) {
        byte[] bytes = trade.toJsonAsBytes();
        // The bytes could be null if there is an issue with the JSON serialization by
        // the Jackson JSON library.
        if (bytes == null) {
            LOG.warn("Could not get JSON bytes for stock trade");
            return;
        }

        LOG.info("Putting trade: " + trade.toString());
        // We use the ticker symbol as the partition key, explained in the Supplemental
        // Information section below.
        PutRecordRequest request = PutRecordRequest.builder().partitionKey(trade.getTickerSymbol())
                .streamName(streamName).data(SdkBytes.fromByteArray(bytes)).build();
        try {
            kinesisClient.putRecord(request).get();
        } catch (InterruptedException e) {
            LOG.info("Interrupted, assuming shutdown.");
        } catch (ExecutionException e) {
            LOG.error("Exception while sending data to Kinesis. Will try again next cycle.", e);
        }

        // v1.x code
        // byte[] bytes = trade.toJsonAsBytes();
        // // The bytes could be null if there is an issue with the JSON serialization
        // by
        // // the Jackson JSON library.
        // if (bytes == null) {
        // LOG.warn("Could not get JSON bytes for stock trade");
        // return;
        // }

        // LOG.info("Putting trade: " + trade.toString());
        // PutRecordRequest putRecord = new PutRecordRequest();
        // putRecord.setStreamName(streamName);
        // // We use the ticker symbol as the partition key, explained in the
        // Supplemental
        // // Information section below.
        // putRecord.setPartitionKey(trade.getTickerSymbol());
        // putRecord.setData(ByteBuffer.wrap(bytes));

        // try {
        // kinesisClient.putRecord(putRecord);
        // } catch (AmazonClientException ex) {
        // LOG.warn("Error sending record to Amazon Kinesis.", ex);
        // }
    }

    public static void main(final String[] args) throws Exception {
        checkUsage(args);

        final String streamName = args[0];
        final String regionName = args[1];
        final Region region = Region.of(regionName);
        if (region == null) {
            System.err.println(regionName + " is not a valid AWS region.");
            System.exit(1);
        }

        final KinesisAsyncClient kinesisClient = KinesisClientUtil
                .createKinesisAsyncClient(KinesisAsyncClient.builder().region(region));

        // Validate that the stream exists and is active
        validateStream(kinesisClient, streamName);

        // Repeatedly send stock trades with a 100 milliseconds wait in between
        final StockTradeGenerator stockTradeGenerator = new StockTradeGenerator();
        while (true) {
            final StockTrade trade = stockTradeGenerator.getRandomTrade();
            sendStockTrade(trade, kinesisClient, streamName);
            Thread.sleep(100);
        }
    }

}
