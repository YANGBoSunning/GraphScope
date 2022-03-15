/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.graphscope.example.giraph;

import org.apache.giraph.conf.LongConfOption;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.BasicComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.io.formats.TextVertexOutputFormat;
import org.apache.giraph.worker.WorkerContext;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Demonstrates the basic Pregel shortest paths implementation.
 */
// @Algorithm(
//    name = "Shortest paths",
//    description = "Finds all shortest paths from a selected vertex"
// )
public class SSSP
        extends BasicComputation<LongWritable, DoubleWritable, DoubleWritable, DoubleWritable> {

    /**
     * The shortest paths id
     */
    public static final LongConfOption SOURCE_ID =
            new LongConfOption("SimpleShortestPathsVertex.sourceId", 1, "The shortest paths id");
    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(SSSP.class);

    /**
     * Is this vertex the source id?
     *
     * @param vertex Vertex
     * @return True if the source id
     */
    private boolean isSource(Vertex<LongWritable, ?, ?> vertex) {
        return vertex.getId().get() == SOURCE_ID.get(getConf());
    }

    @Override
    public void compute(
            Vertex<LongWritable, DoubleWritable, DoubleWritable> vertex,
            Iterable<DoubleWritable> messages)
            throws IOException {
        if (getSuperstep() == 0) {
            vertex.setValue(new DoubleWritable(Double.MAX_VALUE));
        }
        double minDist = isSource(vertex) ? 0d : Double.MAX_VALUE;
        for (DoubleWritable message : messages) {
            minDist = Math.min(minDist, message.get());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Vertex "
                            + vertex.getId()
                            + " got minDist = "
                            + minDist
                            + " vertex value = "
                            + vertex.getValue());
        }
        if (minDist < vertex.getValue().get()) {
            vertex.setValue(new DoubleWritable(minDist));
            for (Edge<LongWritable, DoubleWritable> edge : vertex.getEdges()) {
                double distance = minDist + edge.getValue().get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug(
                            "Vertex "
                                    + vertex.getId()
                                    + " sent to "
                                    + edge.getTargetVertexId()
                                    + " = "
                                    + distance);
                }
                sendMessage(edge.getTargetVertexId(), new DoubleWritable(distance));
            }
        }
        vertex.voteToHalt();
    }

    public static class SSSPWorkerContext extends WorkerContext {

        private static final Logger LOG = LoggerFactory.getLogger(SSSPWorkerContext.class);

        /**
         * Initialize the WorkerContext. This method is executed once on each Worker before the
         * first superstep starts.
         *
         * @throws IllegalAccessException Thrown for getting the class
         * @throws InstantiationException Expected instantiation in this method.
         */
        @Override
        public void preApplication() throws InstantiationException, IllegalAccessException {
            LOG.info("PreApplication");
        }

        /**
         * Finalize the WorkerContext. This method is executed once on each Worker after the last
         * superstep ends.
         */
        @Override
        public void postApplication() {
            LOG.info("PostApplication");
        }

        /**
         * Execute user code. This method is executed once on each Worker before each superstep
         * starts.
         */
        @Override
        public void preSuperstep() {
            LOG.info("PreSuperstep : " + getSuperstep());
        }

        /**
         * Execute user code. This method is executed once on each Worker after each superstep
         * ends.
         */
        @Override
        public void postSuperstep() {
            LOG.info("PostSuperstep: " + getSuperstep());
        }
    }

    /**
     * Simple VertexOutputFormat.
     */
    public static class SimpleSuperstepVertexOutputFormat
            extends TextVertexOutputFormat<LongWritable, DoubleWritable, DoubleWritable> {

        /**
         * The factory method which produces the {@link TextVertexWriter} used by this output
         * format.
         *
         * @param context the information about the task
         * @return the text vertex writer to be used
         */
        @Override
        public TextVertexWriter createVertexWriter(TaskAttemptContext context)
                throws IOException, InterruptedException {
            return new SimpleSuperstepVertexWriter();
        }

        /**
         * Simple VertexWriter.
         */
        public class SimpleSuperstepVertexWriter extends TextVertexWriter {

            @Override
            public void writeVertex(Vertex<LongWritable, DoubleWritable, DoubleWritable> vertex)
                    throws IOException, InterruptedException {
                getRecordWriter()
                        .write(
                                new Text(vertex.getId().toString()),
                                new Text(vertex.getValue().toString()));
            }
        }
    }
}