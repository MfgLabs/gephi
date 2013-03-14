/*
 Copyright 2013 MFG Labs
 Authors : Julian Bilcke, Joachim de Lezardière
 Website : http://www.mfglabs.com

 This file is part of Gephi.

 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

 Copyright 2011 Gephi Consortium. All rights reserved.

 The contents of this file are subject to the terms of either the GNU
 General Public License Version 3 only ("GPL") or the Common
 Development and Distribution License("CDDL") (collectively, the
 "License"). You may not use this file except in compliance with the
 License. You can obtain a copy of the License at
 http://gephi.org/about/legal/license-notice/
 or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
 specific language governing permissions and limitations under the
 License.  When distributing the software, include this License Header
 Notice in each file and include the License files at
 /cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
 License Header, with the fields enclosed by brackets [] replaced by
 your own identifying information:
 "Portions Copyrighted [year] [name of copyright owner]"

 If you wish your version of this file to be governed by only the CDDL
 or only the GPL Version 3, indicate your decision by adding
 "[Contributor] elects to include this software in this distribution
 under the [CDDL or GPL Version 3] license." If you do not indicate a
 single choice of license, a recipient has the option to distribute
 your version of this file under either the CDDL, the GPL Version 3 or
 to extend the choice of license to its licensees as provided above.
 However, if you add GPL Version 3 code and therefore, elected the GPL
 Version 3 license, then the option applies only if the new code is
 made subject to such option by the copyright holder.

 Contributor(s):

 Portions Copyrighted 2011 Gephi Consortium.
 */
package org.gephi.layout.plugin.treeLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.MissingResourceException;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeOrigin;
import org.gephi.data.attributes.api.AttributeTable;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.data.attributes.api.Estimator;
import org.gephi.data.attributes.type.Interval;
import org.gephi.data.attributes.type.TimeInterval;
import org.gephi.dynamic.api.DynamicController;
import org.gephi.dynamic.api.DynamicGraph;
import org.gephi.dynamic.api.DynamicModel;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.EdgeIterable;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.graph.api.NodeIterable;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Algorithm's paper: "Improving Walker's Algorithm to Run in Linear Time" by C.
 * Buchheim, M. Jünger, S. Leipert
 *
 * Gephi implementation by
 * @author Juilan Bilcke
 */
public class TreeLayout extends AbstractTreeLayout {

    //Graph
    private float minx = 0.0f;
    private float maxx = 0.0f;
    private float miny = 0.0f;
    private float maxy = 0.0f;
    private int levels = 0;
        
    //Properties
    private Double width = 30.0;
    private Double height = 400.0;
    private Boolean isPolar = false;
    private Double radius = 20.0;
    private Boolean autoResize = true;
    private Boolean createDepthAttribute = false;
    private Boolean continuous = false;
    private Double spacingCoefficient = 5.0;


    
    private Comparator<Node> nodeComparator = new Comparator<Node>() {
        @Override
        public int compare(Node one, Node two) {
            return new Integer(one.getId()).compareTo(new Integer(two.getId()));
        }
    };

    public TreeLayout(LayoutBuilder layoutBuilder) {
        super(layoutBuilder);
    }

    @Override
    public void resetPropertiesValues() {
        width = 30.0;
        height = 400.0;
        isPolar = false;
        radius = 20.0;
        autoResize = true;
        createDepthAttribute = false;
        continuous = false;
        spacingCoefficient = 5.0;
        // current state
        // computed stats
        levels = 0;
    }

    @Override
    public void initAlgo() {
        this.converged = false;
    }

    public int setupDepth(Node n, int depth) {
        TreeData data = n.getNodeData().getLayoutData();
        data.depth = depth;
        int max = depth;
        for (Node child : data.children) {
            int d = setupDepth(child, depth + 1);
            if (d > max) {
                max = d;
            }
        }
        return max;
    }

    @Override
    public void goAlgo() {

        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        graphModel = graphController.getModel();

        if (graphModel == null) {
            System.out.println("Error, graph model is null");
            if (!continuous) {
                converged = true;
            }
            return;
        }
        DirectedGraph graph = graphModel.getDirectedGraphVisible();

        /*        
         DynamicController dynamicController = Lookup.getDefault().lookup(DynamicController.class);
         DynamicModel dynamicModel = dynamicController.getModel(); 
         boolean isDynamic = dynamicModel.isDynamicGraph();
         if ( isDynamic && dynamicModel != null) {
         DynamicGraph dynamicGraph = dynamicModel.createDynamicGraph(graph);
         TimeInterval timeInt = dynamicModel.getVisibleInterval();
         dynamicGraph.setInterval(timeInt);
         // Presumably the graph at the given time interval
         graph = dynamicGraph.getSnapshotGraph(timeInt.getLow(), timeInt.getHigh());
         Estimator estimator = dynamicModel.getEstimator();
         // Handy for converting DynamicDouble to appropriate primitive
         Interval currentInt = new Interval(timeInt.getLow(), timeInt.getHigh());
         } else {
         graph = graphModel.getGraph();
         }*/

        Node[] nodes = graph.getNodes().toArray(); // so .length etc.. can work
        for (Node n : nodes) {
            getLayoutData(n);
            if (graph.getInDegree(n) == 0) {
                root = n;
            }
        }
        if (root == null) {
            System.out.println("Error no root node, borting Tree layout.");
            if (!continuous) {
                converged = true;
            }
            return;
        }

        for (Edge edge : graph.getEdges()) {
            Node source = edge.getSource();
            Node target = edge.getTarget();
            TreeData sourceLayoutData = getLayoutData(source);
            TreeData targetLayoutData = getLayoutData(target);
            targetLayoutData.modifier = 0;
            targetLayoutData.ancestror = target;
            targetLayoutData.thread = root;
            sourceLayoutData.children = DataUtils.concat(sourceLayoutData.children, new Node[]{target});
            // /!\ targetLayoutData.number will be initialized during firstWalk
            Arrays.sort(sourceLayoutData.children, nodeComparator);
        }
        getLayoutData(root).thread = null;

        System.out.println("root: " + root.getId());
        levels = setupDepth(root, 0);

        firstWalk(root, 0);
        secondWalk(root, -getLayoutData(root).prelim);

        for (Node n : nodes) {
            TreeData d = n.getNodeData().getLayoutData();
            if (!isPolar) {
                // minir adjustment for the root
                if (n.getId() == root.getId()) {
                    double x = minx + (Math.abs(maxx - minx) * 0.5);
                    System.out.println("" + x + " = (" + maxx + " - " + minx + ") * 0.5 = (" + (maxx - minx) + ") * 0.5");
                }
                n.getNodeData().setX(new Float(this.width * d.x));
                n.getNodeData().setY(new Float(this.height * d.y));

            } else {
                double angle = Math.toRadians(360 * DataUtils.normalize(d.x, minx, maxx));
                double r = (this.radius * ((this.autoResize) ? nodes.length : 1.0)) * DataUtils.normalize(d.y, miny, maxy);
                n.getNodeData().setX(new Float(r * Math.cos(angle)));
                n.getNodeData().setY(new Float(r * Math.sin(angle)));
            }
        }
        if (!continuous) {
            converged = true;
        }
    }

    private void firstWalk(Node v, int num) {
        TreeData vp = getLayoutData(v);
        vp.number = num; // todo create the attribute model?
        if (vp.children.length == 0) {
            vp.prelim = 0;
            Node leftSibling = getLeftSibling(v);
            if (leftSibling != null) {
                vp.prelim = getLayoutData(leftSibling).prelim + spacing(leftSibling, v);
            }
        } else {
            Node defaultAncestor = vp.children[0];
            int i = 0;
            for (Node w : vp.children) {
                firstWalk(w, i++);
                defaultAncestor = apportion(w, defaultAncestor);
            }
            executeShifts(v);
            TreeData leftMostd = getLeftMost(vp.children).getNodeData().getLayoutData();
            TreeData rightMostd = getRightMost(vp.children).getNodeData().getLayoutData();
            float midpoint = 0.5f * (leftMostd.prelim + rightMostd.prelim);
            Node leftSibling = getLeftSibling(v);
            if (leftSibling != null) {
                vp.prelim = getLayoutData(leftSibling).prelim + spacing(v, leftSibling);
                vp.modifier = vp.prelim - midpoint;
            } else {
                vp.prelim = midpoint;
            }
        }
    }

    private void secondWalk(Node v, float m) {
        TreeData vd = v.getNodeData().getLayoutData();
        vd.x = vd.prelim + m;
        vd.y = vd.depth;
        if (vd.x < minx) {
            minx = vd.x;
        }
        if (vd.y < miny) {
            miny = vd.y;
        }
        if (vd.x > maxx) {
            maxx = vd.x;
        }
        if (vd.y > maxy) {
            maxy = vd.y;
        }
        for (Node child : vd.children) {
            secondWalk(child, m + vd.modifier);
        }
    }

    private Node getLeftSibling(Node v) {
        TreeData vd = v.getNodeData().getLayoutData();
        Node parent = getParent(v);
        if (parent != null) {
            Node leftSibling = null;
            for (Node w : getLayoutData(parent).children) {
                if (w.getId() == v.getId()) {
                    if (leftSibling != null) {
                        return leftSibling;
                    } else {
                        return null;
                    }
                }
                leftSibling = w;
            }
        }
        return null;
    }

    private Node getLeftMost(Node[] nodes) {
        return (nodes.length > 0) ? nodes[0] : null;
    }

    private Node getRightMost(Node[] nodes) {
        return (nodes.length > 0) ? nodes[nodes.length - 1] : null;
    }

    private Node getLeftMost(Node n) {
        return getLeftMost(getLayoutData(n).children);
    }

    private Node getRightMost(Node n) {
        return getRightMost(getLayoutData(n).children);
    }

    private Node getLeftMostSibling(Node n) {
        Node parent = getParent(n);
        return (parent != null) ? getLeftMost(parent) : n;
    }

    private Node getNextLeft(Node v) {
        Node leftMostChild = getLeftMost(v);
        Node res = (leftMostChild != null) ? leftMostChild : getLayoutData(v).thread;
        return (res.getId() == root.getId()) ? null : res;
    }

    private Node getNextRight(Node v) {
        Node rightMostChild = getRightMost(v);
        Node res = (rightMostChild != null) ? rightMostChild : getLayoutData(v).thread;
        return (res.getId() == root.getId()) ? null : res;
    }

    private void moveSubtree(Node wm, Node wp, float shift) {
        TreeData wmd = getLayoutData(wm);
        TreeData wpd = getLayoutData(wp);
        wpd.change -= shift / (wpd.number - wmd.number);
        wpd.shift += shift;
        wmd.change += shift / (wpd.number - wmd.number);
        wpd.prelim += shift;
        wpd.modifier += shift;
    }

    private void executeShifts(Node v) {
        Node[] children = getLayoutData(v).children;
        float shift = 0, change = 0;
        for (int i = children.length - 1; i > -1; i--) {
            Node w = children[i];
            TreeData wd = getLayoutData(w);
            wd.prelim += shift;
            wd.modifier += shift;
            change += wd.change;
            shift += wd.shift + change;
        }

    }

    private boolean hasChild(Node v, Node candidate) {
        if (v == null) {
            return false;
        }
        Node candidateParent = getParent(candidate);
        if (candidateParent == null) {
            return false;
        } else {
            return (v.getId() == candidateParent.getId());
        }
    }

    private Node getParent(Node v) {
        if (v == null) {
            return null;
        } else {
            Node[] parents = graphModel.getDirectedGraphVisible().getPredecessors(v).toArray();
            return (parents.length == 0) ? null : parents[0];
        }
    }

    private Node getAncestor(Node vim, Node v, Node defaultAncestror) {
        if (hasChild(getParent(v), getParent(vim))) {
            return getParent(vim);
        } else {
            return defaultAncestror;
        }
    }

    private float spacing(Node vim, Node vip) {
        return new Float(this.spacingCoefficient) * (getParent(vim) == getParent(vip) ? 2 : 1) / getLayoutData(vim).depth;
    }

    public TreeData getOrSetLayout(Node n, TreeData data) {
        if (n.getNodeData().getLayoutData() == null || !(n.getNodeData().getLayoutData() instanceof TreeData)) {
            n.getNodeData().setLayoutData(data);
        }
        return n.getNodeData().getLayoutData();
    }

    private TreeData getLayoutData(Node n) {
        return getOrSetLayout(n, new TreeData());
    }

    private Node apportion(Node v, Node a) {
        if (getLeftSibling(v) != null) {
            Node vip = v;
            Node vop = v;
            Node vim = getLeftSibling(v);
            Node vom = getLeftMostSibling(vip);
            float sip = getLayoutData(vip).modifier;
            float sop = getLayoutData(vop).modifier;
            float sim = getLayoutData(vim).modifier;
            float som = getLayoutData(vom).modifier;
            int i = 0;
            while (getNextRight(vim) != null && getNextLeft(vip) != null) {
                vim = getNextRight(vim);
                vip = getNextLeft(vip);
                vom = getNextLeft(vom);
                vop = getNextRight(vop);
                getLayoutData(vop).ancestror = v;
                float shift = (getLayoutData(vim).prelim + sim) - (getLayoutData(vip).prelim + sip) + spacing(vim, vip);
                if (shift > 0) {
                    moveSubtree(getAncestor(vim, v, a), v, shift);
                    sip += shift;
                    sop += shift;
                }
                sim += getLayoutData(vim).modifier;
                sip += getLayoutData(vip).modifier;
                som += getLayoutData(vom).modifier;
                sop += getLayoutData(vop).modifier;
            }
            if (getNextRight(vim) != null && getNextRight(vop) == null) {
                getLayoutData(vop).thread = getNextRight(vim);
                getLayoutData(vop).modifier += sim - sop;
            }
            if (getNextLeft(vip) != null && getNextLeft(vom) == null) {
                getLayoutData(vom).thread = getNextLeft(vip);
                getLayoutData(vom).modifier += sip - som;
                a = v;
            }
        }
        return a;
    }

    @Override
    public void endAlgo() {
        for (Node n : graphModel.getGraph().getNodes()) {
            n.getNodeData().setLayoutData(null);
        }
    }

    @Override
    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();
        final String TREE_LAYOUT = "Tree Layout";

        try {
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.spacing.name"),
                    TREE_LAYOUT,
                    "TreeLayout.spacing.name",
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.spacing.desc"),
                    "getSpacingCoefficient", "setSpacingCoefficient"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.width.name"),
                    TREE_LAYOUT,
                    "TreeLayout.width.name",
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.width.desc"),
                    "getWidth", "setWidth"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.height.name"),
                    TREE_LAYOUT,
                    "TreeLayout.height.name",
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.height.desc"),
                    "getHeight", "setHeight"));
            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.polar.name"),
                    TREE_LAYOUT,
                    "TreeLayout.polar.name",
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.polar.desc"),
                    "getIsPolar", "setIsPolar"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.radius.name"),
                    TREE_LAYOUT,
                    "TreeLayout.radius.name",
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.radius.desc"),
                    "getRadius", "setRadius"));
            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.continuous.name"),
                    TREE_LAYOUT,
                    "TreeLayout.continuous.name",
                    NbBundle.getMessage(TreeLayout.class, "TreeLayout.continuous.desc"),
                    "getContinuous", "setContinuous"));
        } catch (Exception e) {
            //Exceptions.printStackTrace(e);
            //System.out.println("couldn't find translation, using default branding");
            try {
                properties.add(LayoutProperty.createProperty(
                        this, Integer.class,
                        "Root id",
                        TREE_LAYOUT,
                        "TreeLayout.root.name",
                        "Root node id",
                        "getRootId", "setRootId"));
                properties.add(LayoutProperty.createProperty(
                        this, Double.class,
                        "Spacing coefficient",
                        TREE_LAYOUT,
                        "TreeLayout.spacing.name",
                        "Spacing coefficient",
                        "getSpacingCoefficient", "setSpacingCoefficient"));
                properties.add(LayoutProperty.createProperty(
                        this, Double.class,
                        "Tree width",
                        TREE_LAYOUT,
                        "TreeLayout.width.name",
                        "Tree width",
                        "getWidth", "setWidth"));
                properties.add(LayoutProperty.createProperty(
                        this, Double.class,
                        "Tree height",
                        TREE_LAYOUT,
                        "TreeLayout.height.name",
                        "Tree height",
                        "getHeight", "setHeight"));
                properties.add(LayoutProperty.createProperty(
                        this, Boolean.class,
                        "Polar projection",
                        TREE_LAYOUT,
                        "TreeLayout.polar.name",
                        "Project the tree over a circle?",
                        "getIsPolar", "setIsPolar"));
                properties.add(LayoutProperty.createProperty(
                        this, Double.class,
                        "Radius",
                        TREE_LAYOUT,
                        "TreeLayout.radius.name",
                        "Circle radius",
                        "getRadius", "setRadius"));
                /*
                properties.add(LayoutProperty.createProperty(
                        this, Boolean.class,
                        "Continuous",
                        TREE_LAYOUT,
                        "TreeLayout.continuous.name",
                        "Continuous mode (realtime update)",
                        "getContinuous", "setContinuous"));
                        * */
            } catch (NoSuchMethodException ex) {
                Exceptions.printStackTrace(ex);
            }
        }


        return properties.toArray(new LayoutProperty[0]);
    }

    public Boolean getIsPolar() {
        return this.isPolar;
    }

    public void setIsPolar(Boolean isPolar) {
        this.isPolar = isPolar;
    }

    public Double getRadius() {
        return this.radius;
    }

    public void setRadius(Double radius) {
        this.radius = radius;
    }

    public Double getWidth() {
        return this.width;
    }

    public void setWidth(Double width) {
        this.width = width;
    }

    public Double getHeight() {
        return this.height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Boolean getAutoResize() {
        return this.autoResize;
    }

    public void setAutoResize(Boolean autoResize) {
        this.autoResize = autoResize;
    }

    public void setCreateDepthAttribute(Boolean createDepthAttribute) {
        this.createDepthAttribute = createDepthAttribute;
    }

    public Boolean getCreateDepthAttribute() {
        return this.createDepthAttribute;
    }

    public void setContinuous(Boolean continuous) {
        this.continuous = continuous;
    }

    public Boolean getContinuous() {
        return this.continuous;
    }

    public void setSpacingCoefficient(Double spacingCoefficient) {
        this.spacingCoefficient = spacingCoefficient;
    }

    public Double getSpacingCoefficient() {
        return this.spacingCoefficient;
    }
}
