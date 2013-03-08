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
 * Algorithm's paper:
 * "Improving Walker's Algorithm to Run in Linear Time"
 * by C. Buchheim, M. Jünger, S. Leipert
 * 
 * Gephi implementation:
 * @author Juilan Bilcke
 */
public class TreeLayout extends AbstractLayout implements Layout {

    //Graph
    protected HierarchicalGraph graph;
    
    //Properties
    private int rootId = 1;
        
    // current state
    private boolean converged;
    private Node root = null;
    
    // computed stats
    private int levels = 0;
   
    private Comparator<Node> nodeComparator = new Comparator<Node>() {
                @Override public int compare(Node one, Node two) {
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

    public HierarchicalTreeNodeLayoutData getOrSetLayout(Node n, HierarchicalTreeNodeLayoutData data) {
            if (n.getNodeData().getLayoutData() == null || !(n.getNodeData().getLayoutData() instanceof HierarchicalTreeNodeLayoutData)) {
                n.getNodeData().setLayoutData(data);
            }
            return n.getNodeData().getLayoutData();
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
            System.out.println(" - source: " + source.getId());
            System.out.println(" - target: " + target.getId());
            System.out.println("");
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
                Node[] newChild = { target };
                sourceLayoutData.children = concat(sourceLayoutData.children, newChild); 

                Arrays.sort(sourceLayoutData.children, nodeComparator);

         }

        System.out.println("root: " + root.getId());
        System.out.println("Setting depth for each nodes starting from root..");
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

        HierarchicalTreeNodeLayoutData rootLayoutData = root.getNodeData().getLayoutData();
        System.out.println("STEP 2. CALLING FIRST WALK");
        firstWalk(root);
        
        System.out.println("STEP 3. CALLING SECOND WALK");
        secondWalk(root, - rootLayoutData.prelim);
        
        System.out.println("STEP 4. copying layout coordinates into nodes coordinates");
        for (Node n : nodes) {
            HierarchicalTreeNodeLayoutData layoutData = n.getNodeData().getLayoutData();
            layoutData.x = 0;
            layoutData.y = 0;
            
            NodeData nodeData = n.getNodeData();
            nodeData.setX(layoutData.x);
            nodeData.setY(layoutData.y);
        }

        graph.readUnlock();
         converged = true;
    }

    private void firstWalk(Node v) {
        System.out.println("calling firstWalk on " + v.getId());
        HierarchicalTreeNodeLayoutData data = v.getNodeData().getLayoutData();
        if (data.children.length == 0) {
            System.out.println("is a leaf");
            data.prelim = 0;
        } else {
            Node defaultAncestror = data.children[0];
            for (Node w : data.children) {
                firstWalk(w);
                apportion(w, defaultAncestror);
            }
            executeShifts(v);
            HierarchicalTreeNodeLayoutData leftMostChildData = leftMost(data.children).getNodeData().getLayoutData();
            HierarchicalTreeNodeLayoutData rightMostChildData = rightMost(data.children).getNodeData().getLayoutData();
            float midpoint = 0.5f * ( leftMostChildData.prelim + rightMostChildData.prelim );
            
        }
    }
     private void secondWalk(Node v, float m) {
         System.out.println(" -> calling secondWalk on " + v.getId() + ", m is: " + m);
        HierarchicalTreeNodeLayoutData data = v.getNodeData().getLayoutData();
        data.x = data.prelim + m;
        data.y = data.depth;
        for (Node child : data.children) {
            secondWalk(child, m + data.modifier);
        }
        
    }
     
     private Node leftSibling(Node v) {
         HierarchicalTreeNodeLayoutData data = v.getNodeData().getLayoutData();
         if (data.parent != null) {
            HierarchicalTreeNodeLayoutData p = data.parent.getNodeData().getLayoutData();
            Node sibling = null;
            for (Node w : p.children) {
                if (w.getId() == v.getId()) {
                    if (sibling != null) {
                        return sibling;
                    } else {
                        return null;
                    }
                }
                sibling = w;
            }
         }
         return null;
     }
     
     private Node rightMostDescendant(Node v) {
         HierarchicalTreeNodeLayoutData data = v.getNodeData().getLayoutData();

         return null;
     }
    private Node leftMost(Node[] nodes) {
        return nodes[0];
    }
    private Node rightMost(Node[] nodes) {
        return nodes[nodes.length - 1];
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
        System.out.println("calling ancestror");
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
