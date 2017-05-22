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

public class AttributeSliceEvent implements ContentEvent{
  private static final long serialVersionUID = 6752449086753238767L;
  private long learningNodeId;
  private int attributeStartingIndex;
  private String key;
  private boolean[] isNominalSlice;
  private double[] attributeSlice;
  private int classValue;
  private double weight;
  private long instanceIndex;
  private boolean isLast;

  public AttributeSliceEvent() {}

  public AttributeSliceEvent(
      long instanceIndex, long learningNodeId, int attributeStartingIndex, String key, boolean[] isNominalSlice, double[] attributeSlice,
      int classValue, double weight) {
    this.instanceIndex = instanceIndex;
    this.learningNodeId = learningNodeId;
    this.attributeStartingIndex = attributeStartingIndex;
    this.key = key;
    this.isNominalSlice = isNominalSlice;
    this.attributeSlice = attributeSlice;
    this.classValue = classValue;
    this.weight = weight;
  }

  public long getInstanceIndex() {
    return instanceIndex;
  }

  public int getClassValue() {
    return classValue;
  }

  public double getWeight() {
    return weight;
  }

  public long getLearningNodeId() {
    return learningNodeId;
  }

  public int getAttributeStartingIndex() {
    return attributeStartingIndex;
  }

  public boolean[] getIsNominalSlice() {
    return isNominalSlice;
  }

  public double[] getAttributeSlice() {
    return attributeSlice;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public void setKey(String key) {
    this.key = key;
  }

  @Override
  public boolean isLastEvent() { // TODO
    return isLast;
  }

  public void setLast(boolean last) {
    isLast = last;
  }
}
