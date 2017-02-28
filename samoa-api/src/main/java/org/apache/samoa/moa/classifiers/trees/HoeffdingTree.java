package org.apache.samoa.moa.classifiers.trees;
/*
 * #%L
 * SAMOA
 * %%
 * Copyright (C) 2014 - 2017 Apache Software Foundation
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

import com.github.javacliparser.FlagOption;
import com.github.javacliparser.FloatOption;
import com.github.javacliparser.IntOption;
import com.github.javacliparser.MultiChoiceOption;

import org.apache.samoa.moa.AbstractMOAObject;
import org.apache.samoa.moa.classifiers.AbstractClassifier;
import org.apache.samoa.moa.classifiers.core.AttributeSplitSuggestion;
import org.apache.samoa.moa.classifiers.core.attributeclassobservers.AttributeClassObserver;
import org.apache.samoa.moa.classifiers.core.splitcriteria.SplitCriterion;
import org.apache.samoa.moa.core.AutoExpandVector;
import org.apache.samoa.moa.core.DoubleVector;
import org.apache.samoa.moa.core.Measurement;
import org.apache.samoa.moa.options.ClassOption;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.moa.classifiers.core.attributeclassobservers.DiscreteAttributeClassObserver;
import org.apache.samoa.moa.classifiers.core.attributeclassobservers.NullAttributeClassObserver;
import org.apache.samoa.moa.classifiers.core.attributeclassobservers.NumericAttributeClassObserver;
import org.apache.samoa.moa.classifiers.core.conditionaltests.InstanceConditionalTest;
import org.apache.samoa.moa.SizeOf;


import java.util.*;

public class HoeffdingTree extends AbstractClassifier {

  private static final long serialVersionUID = 1L;

  @Override
  public String getPurposeString() {
    return "Hoeffding Tree or VFDT.";
  }

  public IntOption maxByteSizeOption = new IntOption("maxByteSize", 'm',
      "Maximum memory consumed by the tree.", 33554432, 0,
      Integer.MAX_VALUE);

  /*
   * public MultiChoiceOption numericEstimatorOption = new MultiChoiceOption(
   * "numericEstimator", 'n', "Numeric estimator to use.", new String[]{
   * "GAUSS10", "GAUSS100", "GK10", "GK100", "GK1000", "VFML10", "VFML100",
   * "VFML1000", "BINTREE"}, new String[]{ "Gaussian approximation evaluating
   * 10 splitpoints", "Gaussian approximation evaluating 100 splitpoints",
   * "Greenwald-Khanna quantile summary with 10 tuples", "Greenwald-Khanna
   * quantile summary with 100 tuples", "Greenwald-Khanna quantile summary
   * with 1000 tuples", "VFML method with 10 bins", "VFML method with 100
   * bins", "VFML method with 1000 bins", "Exhaustive binary tree"}, 0);
   */
  public ClassOption numericEstimatorOption = new ClassOption("numericEstimator",
      'n', "Numeric estimator to use.", NumericAttributeClassObserver.class,
      "GaussianNumericAttributeClassObserver");

  public ClassOption nominalEstimatorOption = new ClassOption("nominalEstimator",
      'd', "Nominal estimator to use.", DiscreteAttributeClassObserver.class,
      "NominalAttributeClassObserver");

  public IntOption memoryEstimatePeriodOption = new IntOption(
      "memoryEstimatePeriod", 'e',
      "How many instances between memory consumption checks.", 1000000,
      0, Integer.MAX_VALUE);

  public IntOption gracePeriodOption = new IntOption(
      "gracePeriod",
      'g',
      "The number of instances a leaf should observe between split attempts.",
      200, 0, Integer.MAX_VALUE);

  public ClassOption splitCriterionOption = new ClassOption("splitCriterion",
      's', "Split criterion to use.", SplitCriterion.class,
      "InfoGainSplitCriterion");

  public FloatOption splitConfidenceOption = new FloatOption(
      "splitConfidence",
      'c',
      "The allowable error in split decision, values closer to 0 will take longer to decide.",
      0.0000001, 0.0, 1.0);

  public FloatOption tieThresholdOption = new FloatOption("tieThreshold",
      't', "Threshold below which a split will be forced to break ties.",
      0.05, 0.0, 1.0);

  public FlagOption binarySplitsOption = new FlagOption("binarySplits", 'b',
      "Only allow binary splits.");

  public FlagOption stopMemManagementOption = new FlagOption(
      "stopMemManagement", 'z',
      "Stop growing as soon as memory limit is hit.");

  public FlagOption removePoorAttsOption = new FlagOption("removePoorAtts",
      'r', "Disable poor attributes.");

  public FlagOption noPrePruneOption = new FlagOption("noPrePrune", 'p',
      "Disable pre-pruning.");

  public static class FoundNode {

    public Node node;

    public SplitNode parent;

    public int parentBranch;

    public FoundNode(Node node, SplitNode parent, int parentBranch) {
      this.node = node;
      this.parent = parent;
      this.parentBranch = parentBranch;
    }
  }

  public static class Node extends AbstractMOAObject {

    private static final long serialVersionUID = 1L;

    protected DoubleVector observedClassDistribution;

    public Node(double[] classObservations) {
      this.observedClassDistribution = new DoubleVector(classObservations);
    }

    public int calcByteSize() {
      return (int) (SizeOf.sizeOf(this) + SizeOf.fullSizeOf(this.observedClassDistribution));
    }

    public int calcByteSizeIncludingSubtree() {
      return calcByteSize();
    }

    public boolean isLeaf() {
      return true;
    }

    public FoundNode filterInstanceToLeaf(Instance inst, SplitNode parent,
                                          int parentBranch) {
      return new FoundNode(this, parent, parentBranch);
    }

    public double[] getObservedClassDistribution() {
      return this.observedClassDistribution.getArrayCopy();
    }

    public double[] getClassVotes(Instance inst, HoeffdingTree ht) {
      return this.observedClassDistribution.getArrayCopy();
    }

    public boolean observedClassDistributionIsPure() {
      return this.observedClassDistribution.numNonZeroEntries() < 2;
    }

    public int subtreeDepth() {
      return 0;
    }

    public double calculatePromise() {
      double totalSeen = this.observedClassDistribution.sumOfValues();
      return totalSeen > 0.0 ? (totalSeen - this.observedClassDistribution.getValue(this.observedClassDistribution.maxIndex()))
          : 0.0;
    }

    @Override
    public void getDescription(StringBuilder sb, int indent) {

    }
  }

  public static class SplitNode extends Node {

    private static final long serialVersionUID = 1L;

    protected InstanceConditionalTest splitTest;

    protected AutoExpandVector<Node> children; // = new AutoExpandVector<Node>();

    @Override
    public int calcByteSize() {
      return super.calcByteSize()
          + (int) (SizeOf.sizeOf(this.children) + SizeOf.fullSizeOf(this.splitTest));
    }

    @Override
    public int calcByteSizeIncludingSubtree() {
      int byteSize = calcByteSize();
      for (Node child : this.children) {
        if (child != null) {
          byteSize += child.calcByteSizeIncludingSubtree();
        }
      }
      return byteSize;
    }

    public SplitNode(InstanceConditionalTest splitTest,
                     double[] classObservations, int size) {
      super(classObservations);
      this.splitTest = splitTest;
      this.children = new AutoExpandVector<Node>(size);
    }

    public SplitNode(InstanceConditionalTest splitTest,
                     double[] classObservations) {
      super(classObservations);
      this.splitTest = splitTest;
      this.children = new AutoExpandVector<Node>();
    }


    public int numChildren() {
      return this.children.size();
    }

    public void setChild(int index, Node child) {
      if ((this.splitTest.maxBranches() >= 0)
          && (index >= this.splitTest.maxBranches())) {
        throw new IndexOutOfBoundsException();
      }
      this.children.set(index, child);
    }

    public Node getChild(int index) {
      return this.children.get(index);
    }

    public int instanceChildIndex(Instance inst) {
      return this.splitTest.branchForInstance(inst);
    }

    @Override
    public boolean isLeaf() {
      return false;
    }

    @Override
    public FoundNode filterInstanceToLeaf(Instance inst, SplitNode parent,
                                          int parentBranch) {
      int childIndex = instanceChildIndex(inst);
      if (childIndex >= 0) {
        Node child = getChild(childIndex);
        if (child != null) {
          return child.filterInstanceToLeaf(inst, this, childIndex);
        }
        return new FoundNode(null, this, childIndex);
      }
      return new FoundNode(this, parent, parentBranch);
    }

    @Override
    public int subtreeDepth() {
      int maxChildDepth = 0;
      for (Node child : this.children) {
        if (child != null) {
          int depth = child.subtreeDepth();
          if (depth > maxChildDepth) {
            maxChildDepth = depth;
          }
        }
      }
      return maxChildDepth + 1;
    }
  }

  public static abstract class LearningNode extends Node {

    private static final long serialVersionUID = 1L;

    public LearningNode(double[] initialClassObservations) {
      super(initialClassObservations);
    }

    public abstract void learnFromInstance(Instance inst, HoeffdingTree ht);
  }

  public static class InactiveLearningNode extends LearningNode {

    private static final long serialVersionUID = 1L;

    public InactiveLearningNode(double[] initialClassObservations) {
      super(initialClassObservations);
    }

    @Override
    public void learnFromInstance(Instance inst, HoeffdingTree ht) {
      this.observedClassDistribution.addToValue((int) inst.classValue(),
          inst.weight());
    }
  }

  public static class ActiveLearningNode extends LearningNode {

    private static final long serialVersionUID = 1L;

    protected double weightSeenAtLastSplitEvaluation;

    protected AutoExpandVector<AttributeClassObserver> attributeObservers = new AutoExpandVector<AttributeClassObserver>();

    protected boolean isInitialized;

    public ActiveLearningNode(double[] initialClassObservations) {
      super(initialClassObservations);
      this.weightSeenAtLastSplitEvaluation = getWeightSeen();
      this.isInitialized = false;
    }

    @Override
    public int calcByteSize() {
      return super.calcByteSize()
          + (int) (SizeOf.fullSizeOf(this.attributeObservers));
    }

    @Override
    public void learnFromInstance(Instance inst, HoeffdingTree ht) {
      if (this.isInitialized == false) {
        this.attributeObservers = new AutoExpandVector<AttributeClassObserver>(inst.numAttributes());
        this.isInitialized = true;
      }
      this.observedClassDistribution.addToValue((int) inst.classValue(),
          inst.weight());
      for (int i = 0; i < inst.numAttributes() - 1; i++) {
        int instAttIndex = modelAttIndexToInstanceAttIndex(i, inst);
        AttributeClassObserver obs = this.attributeObservers.get(i);
        if (obs == null) {
          obs = inst.attribute(instAttIndex).isNominal() ? ht.newNominalClassObserver() : ht.newNumericClassObserver();
          this.attributeObservers.set(i, obs);
        }
        obs.observeAttributeClass(inst.value(instAttIndex), (int) inst.classValue(), inst.weight());
      }
    }

    public double getWeightSeen() {
      return this.observedClassDistribution.sumOfValues();
    }

    public double getWeightSeenAtLastSplitEvaluation() {
      return this.weightSeenAtLastSplitEvaluation;
    }

    public void setWeightSeenAtLastSplitEvaluation(double weight) {
      this.weightSeenAtLastSplitEvaluation = weight;
    }

    public AttributeSplitSuggestion[] getBestSplitSuggestions(
        SplitCriterion criterion, HoeffdingTree ht) {
      List<AttributeSplitSuggestion> bestSuggestions = new LinkedList<AttributeSplitSuggestion>();
      double[] preSplitDist = this.observedClassDistribution.getArrayCopy();
      if (!ht.noPrePruneOption.isSet()) {
        // add null split as an option
        bestSuggestions.add(new AttributeSplitSuggestion(null,
            new double[0][], criterion.getMeritOfSplit(
            preSplitDist,
            new double[][]{preSplitDist})));
      }
      for (int i = 0; i < this.attributeObservers.size(); i++) {
        AttributeClassObserver obs = this.attributeObservers.get(i);
        if (obs != null) {
          AttributeSplitSuggestion bestSuggestion = obs.getBestEvaluatedSplitSuggestion(criterion,
              preSplitDist, i, ht.binarySplitsOption.isSet());
          if (bestSuggestion != null) {
            bestSuggestions.add(bestSuggestion);
          }
        }
      }
      return bestSuggestions.toArray(new AttributeSplitSuggestion[bestSuggestions.size()]);
    }

    public void disableAttribute(int attIndex) {
      this.attributeObservers.set(attIndex,
          new NullAttributeClassObserver());
    }
  }

  protected Node treeRoot;

  protected int decisionNodeCount;

  protected int activeLeafNodeCount;

  protected int inactiveLeafNodeCount;

  protected double inactiveLeafByteSizeEstimate;

  protected double activeLeafByteSizeEstimate;

  protected double byteSizeEstimateOverheadFraction;

  protected boolean growthAllowed;

  public int calcByteSize() {
    int size = (int) SizeOf.sizeOf(this);
    if (this.treeRoot != null) {
      size += this.treeRoot.calcByteSizeIncludingSubtree();
    }
    return size;
  }

  @Override
  public int measureByteSize() {
    return calcByteSize();
  }

  @Override
  public void resetLearningImpl() {
    this.treeRoot = null;
    this.decisionNodeCount = 0;
    this.activeLeafNodeCount = 0;
    this.inactiveLeafNodeCount = 0;
    this.inactiveLeafByteSizeEstimate = 0.0;
    this.activeLeafByteSizeEstimate = 0.0;
    this.byteSizeEstimateOverheadFraction = 1.0;
    this.growthAllowed = true;
    if (this.leafpredictionOption.getChosenIndex()>0) {
      this.removePoorAttsOption = null;
    }
  }

  @Override
  public void trainOnInstanceImpl(Instance inst) {
    if (this.treeRoot == null) {
      this.treeRoot = newLearningNode();
      this.activeLeafNodeCount = 1;
    }
    FoundNode foundNode = this.treeRoot.filterInstanceToLeaf(inst, null, -1);
    Node leafNode = foundNode.node;
    if (leafNode == null) {
      leafNode = newLearningNode();
      foundNode.parent.setChild(foundNode.parentBranch, leafNode);
      this.activeLeafNodeCount++;
    }
    if (leafNode instanceof LearningNode) {
      LearningNode learningNode = (LearningNode) leafNode;
      learningNode.learnFromInstance(inst, this);
      if (this.growthAllowed
          && (learningNode instanceof ActiveLearningNode)) {
        ActiveLearningNode activeLearningNode = (ActiveLearningNode) learningNode;
        double weightSeen = activeLearningNode.getWeightSeen();
        if (weightSeen
            - activeLearningNode.getWeightSeenAtLastSplitEvaluation() >= this.gracePeriodOption.getValue()) {
          attemptToSplit(activeLearningNode, foundNode.parent,
              foundNode.parentBranch);
          activeLearningNode.setWeightSeenAtLastSplitEvaluation(weightSeen);
        }
      }
    }
    if (this.trainingWeightSeenByModel
        % this.memoryEstimatePeriodOption.getValue() == 0) {
      estimateModelByteSizes();
    }
  }

  @Override
  public double[] getVotesForInstance(Instance inst) {
    if (this.treeRoot != null) {
      FoundNode foundNode = this.treeRoot.filterInstanceToLeaf(inst,
          null, -1);
      Node leafNode = foundNode.node;
      if (leafNode == null) {
        leafNode = foundNode.parent;
      }
      return leafNode.getClassVotes(inst, this);
    } else {
      int numClasses = inst.dataset().numClasses();
      return new double[numClasses];
    }
  }

  @Override
  protected Measurement[] getModelMeasurementsImpl() {
    return new Measurement[]{
        new Measurement("tree size (nodes)", this.decisionNodeCount
            + this.activeLeafNodeCount + this.inactiveLeafNodeCount),
        new Measurement("tree size (leaves)", this.activeLeafNodeCount
            + this.inactiveLeafNodeCount),
        new Measurement("active learning leaves",
            this.activeLeafNodeCount),
        new Measurement("tree depth", measureTreeDepth()),
        new Measurement("active leaf byte size estimate",
            this.activeLeafByteSizeEstimate),
        new Measurement("inactive leaf byte size estimate",
            this.inactiveLeafByteSizeEstimate),
        new Measurement("byte size estimate overhead",
            this.byteSizeEstimateOverheadFraction)};
  }

  public int measureTreeDepth() {
    if (this.treeRoot != null) {
      return this.treeRoot.subtreeDepth();
    }
    return 0;
  }

  @Override
  public void getModelDescription(StringBuilder out, int indent) {
//    this.treeRoot.describeSubtree(this, out, indent);
  }

  @Override
  public boolean isRandomizable() {
    return false;
  }

  public static double computeHoeffdingBound(double range, double confidence,
                                             double n) {
    return Math.sqrt(((range * range) * Math.log(1.0 / confidence))
        / (2.0 * n));
  }

  //Procedure added for Hoeffding Adaptive Trees (ADWIN)
  protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
                                   double[] classObservations, int size) {
    return new SplitNode(splitTest, classObservations, size);
  }

  protected SplitNode newSplitNode(InstanceConditionalTest splitTest,
                                   double[] classObservations) {
    return new SplitNode(splitTest, classObservations);
  }


  protected AttributeClassObserver newNominalClassObserver() {
    AttributeClassObserver nominalClassObserver = (AttributeClassObserver) getPreparedClassOption(this.nominalEstimatorOption);
    return (AttributeClassObserver) nominalClassObserver.copy();
  }

  protected AttributeClassObserver newNumericClassObserver() {
    AttributeClassObserver numericClassObserver = (AttributeClassObserver) getPreparedClassOption(this.numericEstimatorOption);
    return (AttributeClassObserver) numericClassObserver.copy();
  }

  protected void attemptToSplit(ActiveLearningNode node, SplitNode parent,
                                int parentIndex) {
    if (!node.observedClassDistributionIsPure()) {
      SplitCriterion splitCriterion = (SplitCriterion) getPreparedClassOption(this.splitCriterionOption);
      AttributeSplitSuggestion[] bestSplitSuggestions = node.getBestSplitSuggestions(splitCriterion, this);
      Arrays.sort(bestSplitSuggestions);
      boolean shouldSplit = false;
      if (bestSplitSuggestions.length < 2) {
        shouldSplit = bestSplitSuggestions.length > 0;
      } else {
        double hoeffdingBound = computeHoeffdingBound(splitCriterion.getRangeOfMerit(node.getObservedClassDistribution()),
            this.splitConfidenceOption.getValue(), node.getWeightSeen());
        AttributeSplitSuggestion bestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 1];
        AttributeSplitSuggestion secondBestSuggestion = bestSplitSuggestions[bestSplitSuggestions.length - 2];
        if ((bestSuggestion.merit - secondBestSuggestion.merit > hoeffdingBound)
            || (hoeffdingBound < this.tieThresholdOption.getValue())) {
          shouldSplit = true;
        }
        // }
        if ((this.removePoorAttsOption != null)
            && this.removePoorAttsOption.isSet()) {
          Set<Integer> poorAtts = new HashSet<Integer>();
          // scan 1 - add any poor to set
          for (int i = 0; i < bestSplitSuggestions.length; i++) {
            if (bestSplitSuggestions[i].splitTest != null) {
              int[] splitAtts = bestSplitSuggestions[i].splitTest.getAttsTestDependsOn();
              if (splitAtts.length == 1) {
                if (bestSuggestion.merit
                    - bestSplitSuggestions[i].merit > hoeffdingBound) {
                  poorAtts.add(new Integer(splitAtts[0]));
                }
              }
            }
          }
          // scan 2 - remove good ones from set
          for (int i = 0; i < bestSplitSuggestions.length; i++) {
            if (bestSplitSuggestions[i].splitTest != null) {
              int[] splitAtts = bestSplitSuggestions[i].splitTest.getAttsTestDependsOn();
              if (splitAtts.length == 1) {
                if (bestSuggestion.merit
                    - bestSplitSuggestions[i].merit < hoeffdingBound) {
                  poorAtts.remove(new Integer(splitAtts[0]));
                }
              }
            }
          }
          for (int poorAtt : poorAtts) {
            node.disableAttribute(poorAtt);
          }
        }
      }
      if (shouldSplit) {
        AttributeSplitSuggestion splitDecision = bestSplitSuggestions[bestSplitSuggestions.length - 1];
        if (splitDecision.splitTest == null) {
          // preprune - null wins
          deactivateLearningNode(node, parent, parentIndex);
        } else {
          SplitNode newSplit = newSplitNode(splitDecision.splitTest,
              node.getObservedClassDistribution(),splitDecision.numSplits() );
          for (int i = 0; i < splitDecision.numSplits(); i++) {
            Node newChild = newLearningNode(splitDecision.resultingClassDistributionFromSplit(i));
            newSplit.setChild(i, newChild);
          }
          this.activeLeafNodeCount--;
          this.decisionNodeCount++;
          this.activeLeafNodeCount += splitDecision.numSplits();
          if (parent == null) {
            this.treeRoot = newSplit;
          } else {
            parent.setChild(parentIndex, newSplit);
          }
        }
        // manage memory
        enforceTrackerLimit();
      }
    }
  }

  public void enforceTrackerLimit() {
    if ((this.inactiveLeafNodeCount > 0)
        || ((this.activeLeafNodeCount * this.activeLeafByteSizeEstimate + this.inactiveLeafNodeCount
        * this.inactiveLeafByteSizeEstimate)
        * this.byteSizeEstimateOverheadFraction > this.maxByteSizeOption.getValue())) {
      if (this.stopMemManagementOption.isSet()) {
        this.growthAllowed = false;
        return;
      }
      FoundNode[] learningNodes = findLearningNodes();
      Arrays.sort(learningNodes, new Comparator<FoundNode>() {

        @Override
        public int compare(FoundNode fn1, FoundNode fn2) {
          return Double.compare(fn1.node.calculatePromise(), fn2.node.calculatePromise());
        }
      });
      int maxActive = 0;
      while (maxActive < learningNodes.length) {
        maxActive++;
        if ((maxActive * this.activeLeafByteSizeEstimate + (learningNodes.length - maxActive)
            * this.inactiveLeafByteSizeEstimate)
            * this.byteSizeEstimateOverheadFraction > this.maxByteSizeOption.getValue()) {
          maxActive--;
          break;
        }
      }
      int cutoff = learningNodes.length - maxActive;
      for (int i = 0; i < cutoff; i++) {
        if (learningNodes[i].node instanceof ActiveLearningNode) {
          deactivateLearningNode(
              (ActiveLearningNode) learningNodes[i].node,
              learningNodes[i].parent,
              learningNodes[i].parentBranch);
        }
      }
      for (int i = cutoff; i < learningNodes.length; i++) {
        if (learningNodes[i].node instanceof InactiveLearningNode) {
          activateLearningNode(
              (InactiveLearningNode) learningNodes[i].node,
              learningNodes[i].parent,
              learningNodes[i].parentBranch);
        }
      }
    }
  }

  public void estimateModelByteSizes() {
    FoundNode[] learningNodes = findLearningNodes();
    long totalActiveSize = 0;
    long totalInactiveSize = 0;
    for (FoundNode foundNode : learningNodes) {
      if (foundNode.node instanceof ActiveLearningNode) {
        totalActiveSize += SizeOf.fullSizeOf(foundNode.node);
      } else {
        totalInactiveSize += SizeOf.fullSizeOf(foundNode.node);
      }
    }
    if (totalActiveSize > 0) {
      this.activeLeafByteSizeEstimate = (double) totalActiveSize
          / this.activeLeafNodeCount;
    }
    if (totalInactiveSize > 0) {
      this.inactiveLeafByteSizeEstimate = (double) totalInactiveSize
          / this.inactiveLeafNodeCount;
    }
    int actualModelSize = this.measureByteSize();
    double estimatedModelSize = (this.activeLeafNodeCount
        * this.activeLeafByteSizeEstimate + this.inactiveLeafNodeCount
        * this.inactiveLeafByteSizeEstimate);
    this.byteSizeEstimateOverheadFraction = actualModelSize
        / estimatedModelSize;
    if (actualModelSize > this.maxByteSizeOption.getValue()) {
      enforceTrackerLimit();
    }
  }

  public void deactivateAllLeaves() {
    FoundNode[] learningNodes = findLearningNodes();
    for (int i = 0; i < learningNodes.length; i++) {
      if (learningNodes[i].node instanceof ActiveLearningNode) {
        deactivateLearningNode(
            (ActiveLearningNode) learningNodes[i].node,
            learningNodes[i].parent, learningNodes[i].parentBranch);
      }
    }
  }

  protected void deactivateLearningNode(ActiveLearningNode toDeactivate,
                                        SplitNode parent, int parentBranch) {
    Node newLeaf = new InactiveLearningNode(toDeactivate.getObservedClassDistribution());
    if (parent == null) {
      this.treeRoot = newLeaf;
    } else {
      parent.setChild(parentBranch, newLeaf);
    }
    this.activeLeafNodeCount--;
    this.inactiveLeafNodeCount++;
  }

  protected void activateLearningNode(InactiveLearningNode toActivate,
                                      SplitNode parent, int parentBranch) {
    Node newLeaf = newLearningNode(toActivate.getObservedClassDistribution());
    if (parent == null) {
      this.treeRoot = newLeaf;
    } else {
      parent.setChild(parentBranch, newLeaf);
    }
    this.activeLeafNodeCount++;
    this.inactiveLeafNodeCount--;
  }

  protected FoundNode[] findLearningNodes() {
    List<FoundNode> foundList = new LinkedList<FoundNode>();
    findLearningNodes(this.treeRoot, null, -1, foundList);
    return foundList.toArray(new FoundNode[foundList.size()]);
  }

  protected void findLearningNodes(Node node, SplitNode parent,
                                   int parentBranch, List<FoundNode> found) {
    if (node != null) {
      if (node instanceof LearningNode) {
        found.add(new FoundNode(node, parent, parentBranch));
      }
      if (node instanceof SplitNode) {
        SplitNode splitNode = (SplitNode) node;
        for (int i = 0; i < splitNode.numChildren(); i++) {
          findLearningNodes(splitNode.getChild(i), splitNode, i,
              found);
        }
      }
    }
  }

  public MultiChoiceOption leafpredictionOption = new MultiChoiceOption(
      "leafprediction", 'l', "Leaf prediction to use.", new String[]{
      "MC", "NB", "NBAdaptive"}, new String[]{
      "Majority class",
      "Naive Bayes",
      "Naive Bayes Adaptive"}, 2);

  public IntOption nbThresholdOption = new IntOption(
      "nbThreshold",
      'q',
      "The number of instances a leaf should observe before permitting Naive Bayes.",
      0, 0, Integer.MAX_VALUE);



  protected LearningNode newLearningNode() {
    return newLearningNode(new double[0]);
  }

  protected LearningNode newLearningNode(double[] initialClassObservations) {
    return new ActiveLearningNode(initialClassObservations);
  }
}

