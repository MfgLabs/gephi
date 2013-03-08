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
 * Ported from:
 * https://bitbucket.org/JonathanGiles/prefusefx/src/prefuse/action/layout/graph/NodeLinkTreeLayout.java
 * http://read.pudn.com/downloads76/sourcecode/java/288603/%E4%BB%A3%E7%A0%81/graphdrawing/Walker.java__.htm
 * @author Juilan Bilcke
 */
public class TreeLayout extends AbstractLayout implements Layout {

    private static final float SPEED_DIVISOR = 800;
    private static final float AREA_MULTIPLICATOR = 10000;
    //Graph
    protected HierarchicalGraph graph;
    //Properties
    private float area;
    private double gravity;
    private double speed;

    private Node root = null;
    private int levels = 0;
       
    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    public TreeLayout(LayoutBuilder layoutBuilder) {
        super(layoutBuilder);
    }

    public void resetPropertiesValues() {
        speed = 1;
        area = 10000;
        gravity = 10;
    }

    public HierarchicalTreeNodeLayoutData getOrSetLayout(Node n, HierarchicalTreeNodeLayoutData data) {
            if (n.getNodeData().getLayoutData() == null || !(n.getNodeData().getLayoutData() instanceof HierarchicalTreeNodeLayoutData)) {
                n.getNodeData().setLayoutData(data);
            }
            return n.getNodeData().getLayoutData();
    }
    public void initAlgo() {
        this.graph = graphModel.getHierarchicalGraphVisible();
        graph.readLock();

        // we first initialize modifiers, threads, and ancestrors
        Edge[] edges = graph.getEdgesAndMetaEdges().toArray();
 
        System.out.println("FIlling up layout data..");
        for (Edge E : edges) {
            Node source = E.getSource();
            Node target = E.getTarget();
            
            HierarchicalTreeNodeLayoutData sourceLayoutData = getOrSetLayout(source, new HierarchicalTreeNodeLayoutData());
            HierarchicalTreeNodeLayoutData targetLayoutData = getOrSetLayout(target, new HierarchicalTreeNodeLayoutData());
            
            // either we are the root node, or one of the children
            if (source == null) {
               if (root == null) {
                 System.out.println("found the root node!");
                 root = target;
               } else {
                  System.out.println("found another root node, ignoring..");
               }
            } else {
                targetLayoutData.parent = source;
                targetLayoutData.modifier = 0;
                targetLayoutData.ancestror = target;

                Node[] newChild = { target };
                sourceLayoutData.children = concat(sourceLayoutData.children, newChild); 
            }

         }
        System.out.println("setting thread for each node");
        for (Node n : graph.getNodes()) {
            HierarchicalTreeNodeLayoutData data = n.getNodeData().getLayoutData();
            data.thread = root;
         }
        System.out.println("Setting depth for each nodes..");
        levels = setDepth(root, 0);
        System.out.println("max depth: " + levels);
        graph.readUnlock();
    }

    public int setDepth(Node n, int depth) {
         HierarchicalTreeNodeLayoutData data = n.getNodeData().getLayoutData();
         data.depth = depth;
         int max = depth;
         for (Node child : data.children) {
             int d = setDepth(child, depth + 1);
             if (d > max) max = d;
         }
         return max;
    }
    
    public void goAlgo() {
        this.graph = graphModel.getHierarchicalGraphVisible();
        graph.readLock();
        Node[] nodes = graph.getNodes().toArray();
        Edge[] edges = graph.getEdgesAndMetaEdges().toArray();

        for (Node n : nodes) {
            if (n.getNodeData().getLayoutData() == null || !(n.getNodeData().getLayoutData() instanceof HierarchicalTreeNodeLayoutData)) {
                n.getNodeData().setLayoutData(new HierarchicalTreeNodeLayoutData());
            }
            HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
            layoutData.dx = 0;
            layoutData.dy = 0;
        }
        for (Node n : nodes) {

            HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
            layoutData.dx = 0;
            layoutData.dy = 0;
        }

        graph.readUnlock();
    }

    private Node firstWalk(Node v) {
        HierarchicalTreeNodeLayoutData data = v.getNodeData().getLayoutData();
        if (data.children.length == 0) {
            data.prelim = 0;
        } else {
            Node defaultAncestror = leftMost(data.children);
        }
    }
    
    private Node leftMost(Node[] nodes) {
        
    }
    private Node nextLeft(Node n) {
        HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
        Node c = null;

        /*
        if ( n.isExpanded() ) c = (Node)n.getFirstChild();
        return ( c != null ? c : getParams(n).thread );
        */
        return c;
    }

    private Node nextRight(Node n) {
        HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
        Node c = null;
        /*
        if ( n.isExpanded() ) c = (Node)n.getLastChild();
        return ( c != null ? c : getParams(n).thread );
        */
        return c;
    }

    private void moveSubtree(Node wm, Node wp, double shift) {
        HierarchicalTreeNodeLayoutData wmLayoutData = wm.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData wpLayoutData = wp.getNodeData().getLayoutData();
        /*
        Params wmp = getParams(wm);
        Params wpp = getParams(wp);
        double subtrees = wpp.number - wmp.number;
        wpp.change -= shift/subtrees;
        wpp.shift += shift;
        wmp.change += shift/subtrees;
        wpp.prelim += shift;
        wpp.mod += shift;
        */
    }

    private void executeShifts(Node n) {
        HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
        /*
        double shift = 0, change = 0;
        for ( Node c = (Node)n.getLastChild();
              c != null; c = (Node)c.getPreviousSibling() )
        {
            Params cp = getParams(c);
            cp.prelim += shift;
            cp.mod += shift;
            change += cp.change;
            shift += cp.shift + change;
        }
        */
    }

    private Node ancestor(Node vim, Node v, Node a) {
        HierarchicalTreeNodeLayoutData vimLayoutData = vim.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData vLayoutData = v.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData aLayoutData = a.getNodeData().getLayoutData();
        /*
        Node p = (Node)v.getParent();
        Params vimp = getParams(vim);
        if ( vimp.ancestor.getParent() == p ) {
            return vimp.ancestor;
        } else {
            return a;
        }
        */
        return null;
    }

    private void secondWalk(Node v, float m) {
        HierarchicalTreeNodeLayoutData data = v.getNodeData().getLayoutData();
        data.x = data.prelim + m;
        data.y = data.depth;
        for (Node child : data.children) {
            secondWalk(child, m + data.modifier);
        }
        
    }
    private Node apportion(Node v, Node a) {
        HierarchicalTreeNodeLayoutData vLayoutData = v.getNodeData().getLayoutData();
        HierarchicalTreeNodeLayoutData aLayoutData = a.getNodeData().getLayoutData();
    /*
        v.getAttributes()
        Node w = v.getPreviousSibling();
        if (w == null) {
            return ancestror;
        }


            Node     vip, vim, vop, vom;
            double   sip, sim, sop, som;

            vip = vop = v;
            vim = w;
            vom = vip.getParent().getFirstChild();

            sip = getParams(vip).mod;
            sop = getParams(vop).mod;
            sim = getParams(vim).mod;
            som = getParams(vom).mod;

            Node nr = nextRight(vim);
            Node nl = nextLeft(vip);
            while ( nr != null && nl != null ) {
                vim = nr;
                vip = nl;
                vom = nextLeft(vom);
                vop = nextRight(vop);
                getParams(vop).ancestor = v;
                double shift = (getParams(vim).prelim + sim) -
                        (getParams(vip).prelim + sip) + spacing(vim,vip,false);
                if ( shift > 0 ) {
                    moveSubtree(getAncestor(vim,v,ancestror), v, shift);
                    sip += shift;
                    sop += shift;
                }
                sim += getParams(vim).mod;
                sip += getParams(vip).mod;
                som += getParams(vom).mod;
                sop += getParams(vop).mod;

                nr = nextRight(vim);
                nl = nextLeft(vip);
            }
            if ( nr != null && nextRight(vop) == null ) {
                Params vopp = getParams(vop);
                vopp.thread = nr;
                vopp.mod += sim - sop;
            }
            if ( nl != null && nextLeft(vom) == null ) {
                Params vomp = getParams(vom);
                vomp.thread = nl;
                vomp.mod += sip - som;
                ancestror = v;
            }
    */
        return a;
    }

    public void endAlgo() {
        for (Node n : graph.getNodes()) {
            n.getNodeData().setLayoutData(null);
        }
    }

    @Override
    public boolean canAlgo() {
        return true;
    }


    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();
        final String TREE_LAYOUT = "Tree Layout";

        try {
            properties.add(LayoutProperty.createProperty(
                    this, Float.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.area.name"),
                    TREE_LAYOUT,
                    "treeLayout.area.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.area.desc"),
                    "getArea", "setArea"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.gravity.name"),
                    TREE_LAYOUT,
                    "treeLayout.gravity.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.gravity.desc"),
                    "getGravity", "setGravity"));
            properties.add(LayoutProperty.createProperty(
                    this, Double.class,
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.speed.name"),
                    TREE_LAYOUT,
                    "treeLayout.speed.name",
                    NbBundle.getMessage(TreeLayout.class, "treeLayout.speed.desc"),
                    "getSpeed", "setSpeed"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return properties.toArray(new LayoutProperty[0]);
    }


    public Float getArea() {
        return area;
    }

    public void setArea(Float area) {
        this.area = area;
    }

    /**
     * @return the gravity
     */
    public Double getGravity() {
        return gravity;
    }

    /**
     * @param gravity the gravity to set
     */
    public void setGravity(Double gravity) {
        this.gravity = gravity;
    }

    /**
     * @return the speed
     */
    public Double getSpeed() {
        return speed;
    }

    /**
     * @param speed the speed to set
     */
    public void setSpeed(Double speed) {
        this.speed = speed;
    }
}
