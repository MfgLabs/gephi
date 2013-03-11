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
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.HierarchicalGraph;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.layout.plugin.AbstractLayout;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.openide.util.NbBundle;

/**
 * Algorithm's paper: "Improving Walker's Algorithm to Run in Linear Time" by C.
 * Buchheim, M. Jünger, S. Leipert
 *
 * Gephi implementation:
 *
 * @author Juilan Bilcke
 */
public class TreeLayout extends AbstractLayout implements Layout {

    public enum Orientation {
        LEFT, TOP, RIGHT, BOTTOM, CIRCULAR
    };
    
    private Orientation orientation = Orientation.TOP;
    
    private static double PI_2 = Math.PI * 2.0;
    
    //Graph
    protected HierarchicalGraph graph;
    
    private float minx = 0.0f;
    private float maxx = 0.0f;
    private float miny = 0.0f;
    private float maxy = 0.0f;
    
    //Properties
    private int rootId = 1;
    // current state
    private boolean converged;
    private Node root = null;
    // computed stats
    private int levels = 0;
    private Comparator<Node> nodeComparator = new Comparator<Node>() {
        @Override
        public int compare(Node one, Node two) {
            return new Integer(one.getId()).compareTo(new Integer(two.getId()));
        }
    };

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public TreeLayout(LayoutBuilder layoutBuilder) {
        super(layoutBuilder);
    }

    public void resetPropertiesValues() {
        rootId = 1;
    }


    public void initAlgo() {
        converged = false;

        this.graph = graphModel.getHierarchicalGraphVisible();
        graph.readLock();

        for (Node n : graph.getNodes()) {
            getOrSetLayout(n, new HierarchicalTreeNodeLayoutData());
            if (n.getId() == rootId) {
                root = n;
            }
        }

        // we first initialize modifiers, threads, and ancestrors
        Edge[] edges = graph.getEdgesAndMetaEdges().toArray();

        System.out.println("STEP 1. INITIALIZING.");
        for (Edge E : edges) {
            Node source = E.getSource();
            Node target = E.getTarget();
            //System.out.println(" - source: " + source.getId());
           // System.out.println(" - target: " + target.getId());
            //System.out.println("");
            HierarchicalTreeNodeLayoutData sourceLayoutData = getOrSetLayout(source, new HierarchicalTreeNodeLayoutData());
            HierarchicalTreeNodeLayoutData targetLayoutData = getOrSetLayout(target, new HierarchicalTreeNodeLayoutData());


            // detect the direction using timestamps
            //System.out.println("source timestamp:  " + source.getNodeData().getAttributes().getValue("timestamp"));
            //System.out.println("target timestamp:  " + source.getNodeData().getAttributes().getValue("timestamp"));
            // Long ts = (Long) source.getAttributes().getValue("timestamp");

            targetLayoutData.parent = source;
            targetLayoutData.modifier = 0;
            targetLayoutData.ancestror = target;
            targetLayoutData.thread = root;

            Node[] newChild = {target};
            sourceLayoutData.children = concat(sourceLayoutData.children, newChild);

           // targetLayoutData.number is now incorrect -- will be corrected during firstWalk
            
            Arrays.sort(sourceLayoutData.children, nodeComparator);

        }

        getLayoutData(root).thread = null;
        
        System.out.println("root: " + root.getId());
        //System.out.println("Setting depth for each nodes starting from root..");
        levels = setupDepth(root, 0);
        System.out.println("max depth: " + levels);
        graph.readUnlock();
    }

    public int setupDepth(Node n, int depth) {
        HierarchicalTreeNodeLayoutData data = n.getNodeData().getLayoutData();
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
    

    public void goAlgo() {
        this.graph = graphModel.getHierarchicalGraphVisible();
        graph.readLock();
        Node[] nodes = graph.getNodes().toArray();
        Edge[] edges = graph.getEdgesAndMetaEdges().toArray();

        HierarchicalTreeNodeLayoutData rootLayoutData = root.getNodeData().getLayoutData();
        System.out.println("STEP 2. CALLING FIRST WALK");
        firstWalk(root, 0);

        System.out.println("STEP 3. CALLING SECOND WALK");
        secondWalk(root, -rootLayoutData.prelim);

        System.out.println("minx = "+minx+ ", miny = "+miny+", maxx = "+maxx + ", maxy = "+maxy);

         for (Node n : nodes)  {    
          HierarchicalTreeNodeLayoutData d = n.getNodeData().getLayoutData();
          
         double x = d.x;
         double y = d.y; 
          

          System.out.println("Node "+n.getId()+" x: "+normalize(d.x, minx, maxx) + ", y: "+normalize(d.y, miny, maxy) );
          
          switch(orientation){
              case TOP:

                if (n.getId() == rootId) {
                    x = (maxx - minx) * 0.5;
                    System.out.println(""+x+" = ("+minx+" - "+maxx+") * 0.5 = ("+(minx - maxx)+") * 0.5");
                }
                x = 30.0 * x; 
                y = 150.0 * y; 

                 break;

              case CIRCULAR:
               // angle
               double angleDegrees = 360 * normalize(x, minx,  maxx);
               System.out.println("in degrees: "+angleDegrees);
               
               double angleRadians = PI_2 * Math.toRadians(angleDegrees);
               System.out.println("in radians: "+Math.toRadians(angleDegrees));

               // radius: 100
               double radius = 2000 * normalize(y, miny,maxy);

                x = radius * Math.cos(angleRadians); 
                y = radius * Math.sin(angleRadians); 
                 break;
                  
               default:
                  break;
          }
           System.out.println("final x: "+x+", y: "+y);     
           n.getNodeData().setX(new Float(x));
           n.getNodeData().setY(new Float(y));
         }
        
        graph.readUnlock();
        converged = true;
    }
    private double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    private void firstWalk(Node v, int num) {
        //System.out.println("calling firstWalk on " + v.getId());
        HierarchicalTreeNodeLayoutData vp = getLayoutData(v);
        vp.number = num;
        if (vp.children.length == 0) {
            //System.out.println("is a leaf");
            vp.prelim = 0;
            //Node leftSibling = getLeftSibling(v);
            //if (leftSibling != null) {
            //    vp.prelim = getLayoutData(leftSibling).prelim + spacing(leftSibling, v);
            //}
            
        } else {
            Node defaultAncestor = vp.children[0];
            int i = 0;
            for (Node w : vp.children) {
                firstWalk(w, i++);
                defaultAncestor = apportion(w, defaultAncestor);
            }
            executeShifts(v);
            HierarchicalTreeNodeLayoutData leftMostd = getLeftMost(vp.children).getNodeData().getLayoutData();
            HierarchicalTreeNodeLayoutData rightMostd = getRightMost(vp.children).getNodeData().getLayoutData();
            float midpoint = 0.5f * (leftMostd.prelim + rightMostd.prelim);

            Node leftSibling = getLeftSibling(v);
            if (leftSibling != null) {
                vp.prelim = getLayoutData(leftSibling).prelim + spacing(v,leftSibling);
                vp.modifier = vp.prelim - midpoint;
            } else {
                vp.prelim = midpoint;
            }
        }
    }

    private void secondWalk(Node v, float m) {
       // System.out.println(" -> calling secondWalk on " + v.getId() + ", m is: " + m);
        HierarchicalTreeNodeLayoutData vd = v.getNodeData().getLayoutData();
        vd.x = vd.prelim + m;
        vd.y = vd.depth;
        if (vd.x < minx) minx = vd.x;
        if (vd.y < miny) miny = vd.y;
        if (vd.x > maxx) maxx = vd.x;
        if (vd.y > maxy) maxy = vd.y;
        
        //System.out.println("x = "+vd.x+ ", y = "+vd.y);

        for (Node child : vd.children) {
            secondWalk(child, m + vd.modifier);
        }

    }

    private Node getLeftSibling(Node v) {
        HierarchicalTreeNodeLayoutData vd = v.getNodeData().getLayoutData();
        if (vd.parent != null) {
            HierarchicalTreeNodeLayoutData pd = vd.parent.getNodeData().getLayoutData();
            Node leftSibling = null;
            for (Node w : pd.children) {
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
        Node parent = getLayoutData(n).parent;
        return (parent != null) ? getLeftMost(parent) : n;
    }

    private Node getNextLeft(Node v) {
       // System.out.println("getNextLeft(" + v.getId() + ")");
        Node leftMostChild = getLeftMost(v);
        Node res = (leftMostChild != null) ? leftMostChild : getLayoutData(v).thread;
        return (res.getId() == rootId) ? null : res;
    }

    private Node getNextRight(Node v) {
        //System.out.println("getNextRight(" + v.getId() + ")");
        Node rightMostChild = getRightMost(v);
        Node res = (rightMostChild != null) ? rightMostChild : getLayoutData(v).thread;
        return (res.getId() == rootId) ? null : res;
    }

    private void moveSubtree(Node wm, Node wp, float shift) {
        HierarchicalTreeNodeLayoutData wmd = wm.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData wpd = wp.getNodeData().getLayoutData();
        //System.out.println("moveSubtree(" + wm.getId() + ", " + wp.getId() + ", " + shift + ")");

        float subtrees = wpd.number - wmd.number;
        //System.out.println("subtrees: " + subtrees+ " = "+wpd.number + " - "+wmd.number);
        wpd.change -= shift / subtrees;
        wpd.shift += shift;
        wmd.change += shift / subtrees;
        wpd.prelim += shift;
        wpd.modifier += shift;

    }

    private void executeShifts(Node v) {
        //System.out.println("executeShifts("+v.getId()+")");
        Node[] children = getLayoutData(v).children;
        float shift = 0, change = 0;
        for (int i = children.length - 1; i > -1; i--) {
            Node w = children[i];
            HierarchicalTreeNodeLayoutData wd = getLayoutData(w);
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
        HierarchicalTreeNodeLayoutData vd = getLayoutData(v);
        Node candidateParent = getParent(candidate);
        if (candidateParent == null) {
            return false;
        }
        return (v.getId() == candidateParent.getId());
    }

    private Node getParent(Node v) {
        if (v == null) {
            return null;
        }
        HierarchicalTreeNodeLayoutData vd = v.getNodeData().getLayoutData();
        return vd.parent;
    }

    private Node getAncestor(Node vim, Node v, Node defaultAncestror) {
        if (hasChild(getParent(v), getParent(vim))) {
            return getParent(vim);
        } else {
            return defaultAncestror;
        }
    }

    private float spacing(Node vim, Node vip) {

        //return (getLayoutData(vim).parent == getLayoutData(vip).parent ? 1 : 2) / getLayoutData(vim).depth;
        return 1.0f;
    }
    
    public HierarchicalTreeNodeLayoutData getOrSetLayout(Node n, HierarchicalTreeNodeLayoutData data) {
        if (n.getNodeData().getLayoutData() == null || !(n.getNodeData().getLayoutData() instanceof HierarchicalTreeNodeLayoutData)) {
            n.getNodeData().setLayoutData(data);
        }
        return n.getNodeData().getLayoutData();
    }
    
    private HierarchicalTreeNodeLayoutData getLayoutData(Node n) {
        return getOrSetLayout(n, new HierarchicalTreeNodeLayoutData());
    }

    private Node apportion(Node v, Node a) {

        //System.out.println("  apportion(" + v.getId() + ")");

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
                
                System.out.println("     -" + i++ + " vim: " + vim.getId() + "  vip: " + vip.getId()+ ", vom: "+vom.getId()+", vop: "+vop.getId());

                getLayoutData(vop).ancestror = v;

                // prefuse does -sip
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

    public void endAlgo() {
        for (Node n : graph.getNodes()) {
            n.getNodeData().setLayoutData(null);
        }
    }

    @Override
    public boolean canAlgo() {
        return !converged;
    }

    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();
        final String TREE_LAYOUT = "Tree Layout";

        try {
            properties.add(LayoutProperty.createProperty(
                    this, Integer.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.root.name"),
                    TREE_LAYOUT,
                    "treeLayout.root.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.root.desc"),
                    "getRoot", "setRoot"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return properties.toArray(new LayoutProperty[0]);
    }

    public Integer getRoot() {
        return rootId;
    }

    public void setRoot(Integer rootId) {
        this.rootId = rootId;
    }
}
