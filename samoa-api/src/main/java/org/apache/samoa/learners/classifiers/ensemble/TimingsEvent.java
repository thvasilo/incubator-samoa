package org.apache.samoa.learners.classifiers.ensemble;
/*
 * #%L
 * SAMOA
 * %%
 * Copyright (C) 2014 - 2015 Apache Software Foundation
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.apache.samoa.core.ContentEvent;

public class TimingsEvent implements ContentEvent {
  private static final long serialVersionUID = 8613159031496662465L;

  private final int measurementID;
  private final int localStatsID;
  private final long attSliceMillis;
  private final long computeEventMillis;
  private boolean isLast;

  public TimingsEvent(int localStatsID, int measureID, long attSliceMillis, long computeEventMillis) {
    this.localStatsID = localStatsID;
    this.measurementID = measureID;
    this.attSliceMillis = attSliceMillis;
    this.computeEventMillis = computeEventMillis;
    isLast = false;
  }

  @Override
  public String getKey() {
    return null;
  }

  @Override
  public void setKey(String key) {

  }

  @Override
  public boolean isLastEvent() {
    return isLast;
  }

  public long getAttSliceMillis() {
    return attSliceMillis;
  }

  public long getComputeEventMillis() {
    return computeEventMillis;
  }

  public int getMeasurementID() {
    return measurementID;
  }

  public int getLocalStatsID() {
    return localStatsID;
  }

  public void setLast(boolean last) {
    isLast = last;
  }
}
