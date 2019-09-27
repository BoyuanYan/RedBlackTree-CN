package rbt;


import java.util.HashSet;
import java.util.Random;
import java.util.TreeMap;

/**
 * 仿照 JDK 1.11 中的TreeMap源码，剥离出来的红黑树的高效实现，并附上了诸多注释。
 * 红黑树是非严格的平衡二叉搜索树。非严格的意思是该树的任意左右子树不要求高度差不超过1，而使用了红黑色做出了最高高度和最低高度
 * 的倍差限制。根据红黑树性质，可能的最高分支高度（红黑节点交替）为可能的最低分支高度（全黑）的两倍。
 */
public class RedBlackTreeFromJDK {

    private Node root;
    private int size = 0;

    public RedBlackTreeFromJDK() {}

    public int size() {
        return this.size;
    }

    /**
     * 返回包含节点k的Node
     * @param k k
     * @return 如果包含，返回node；否则，返回null。
     */
    private Node getNode(int k) {
        Node node = root;
        while (node != null) {
            if (node.val == k)
                return node;
            if (node.val < k)
                node = node.right;
            else
                node = node.left;
        }
        return null;
    }

    public boolean contains(int key) {
        return this.getNode(key) != null;
    }

    /**
     * 向红黑树中加入值k的节点n。
     * 插入的方法分为几步：
     * 1、将值作为红色节点，插入到应该在的叶子节点处；节点颜色为红色是因为，插入红色的话，修复违规的代价会比较小。
     * 2、因为插入之后可能会引起树的不平衡，而且要判断此次插入操作是否违反红黑树性质：
     *    2.1 n.parent=NIL n.bro=whatever n.left=whatever n.right=whatever
     *        这种情况下，就是把k当作根节点插入，然后变为黑色即可
     *    2.2 n.parent!=NIL n.parent.color=black
     *        此时的插入操作不会影响红黑性质的保持。n.bro要么为黑色节点，要么为红色节点。
     *    2.3 n.parent!=NIL n.parent.color=red （此时必定有n.grandpa=NIL或者n.grandpa.color=black）
     *        在n.parent是红色节点的情况下，先根据“红色不连续”性质，保持n颜色不变，令n.parent.color=black && n.grandpa.color=red
     *        如此就引入两个新问题：
     *          i) 自n.grandpa往下的子树中，经过n.parent的路径黑色节点数目不变；但是经过n.uncle的路径黑色节点数-1.
     *          ii) n.grandpa由black变为red，可能会引发n.grandpa和它的父亲节点的冲突，需要内部解决或者递归向上解决。
     *        2.3.1 n.uncle.color=red
     *              只要令n.uncle.color = black,就可以解决问题 i)。然后递归向上解决问题 ii)。
     *        2.3.2 n.uncle.color=black (这种情况下包括n.uncle=NIL)
     *              这种情况下，没有办法通过直接改变颜色解决问题 i)。
     *              2.3.2.1 三角型（折线）。所谓三角型是指n、n.parent、n.grandpa三者形成一个折线，即(n=n.parent.left &&
     *                      n.parent=n.grandpa.right) || (n=n.parent.right && n.parent=n.grandpa.left)
     *                      此时对n进行 (右旋) || (左旋) 操作，将其变为直线型。旋转后，由于n和n.parent关系互换，因此需要将
     *                      两者的颜色互换，即n.color=black && n.parent.color=red。
     *              2.3.2.2 直线型。所谓直线型是指n、n.parent、n.grandpa三者形成一条线段，即(n=n.parent.left &&
     *                      n.parent=n.grandpa.left) || (n=n.parent.right && n.parent=n.grandpa.right)
     *                      此时对n.parent进行 (右旋) || (左旋) 操作。由于此时n.parent是黑色，n.grandpa是红色，所以右旋之后
     *                      局部最高点（旋转前是n.grandpa，旋转后是n.parent）的左子树路径黑色节点数目不变，而右子树路径黑色
     *                      节点数目+1，此时就解决了问题 i)。另外，旋转后n.parent变为局部最高点，而n.parent是黑色的，所以问题
     *                      ii) 也一并解决。
     *
     * @param key key
     */
    public void add(int key) {
        Node t = this.root;
        if (t == null) {
            this.root = new Node(null, null, null, key, true);
            this.size = 1;
        } else {
            Node parent;
            do {
                parent = t;
                if (key == t.val) {
                    return;
                } else if (key < t.val) {
                    t = t.left;
                } else {
                    t = t.right;
                }
            } while(t != null);


            Node e = new Node(parent, null, null, key, true);
            if (key < parent.val) {
                parent.left = e;
            } else {
                parent.right = e;
            }

            this.fixAfterInsertion(e);
            ++this.size;
        }
    }


    /**
     * 在红黑树中删除节点k。可以分为三步
     * 1、寻找被删除节点p。如果不存在，则返回false；如果存在，则继续
     * 2、寻找被删除节点p的替代节点r
     * 3、交换删除节点p和替代节点r的值，删除r
     * 4、删除后调整树结构。
     * @param key key
     * @return 如果树中不包含节点k，则返回false；如果包含，则返回true。
     */
    public void remove(int key) {
        Node p = this.getNode(key);
        if (p != null) {// 如果存在被删除节点
            /*
             * 以下分为几种情况：
             * 1、删除节点有两个后代：
             *    交换替换节点和删除节点的val，对替换节点位置的节点再进行删除操作。相当于把删除操作下放到替换节点的位置进行操作。
             * 2、删除节点没有两个后代：
             *    2.1、如果删除节点没有后代，即删除节点是叶子节点：
             *         此时替换节点是NIL。如果删除节点是红色，则直接删除；如果删除节点是黑色，则进行双黑处理。
             *    2.2、如果删除节点是root，则说明root最多有一个后代，也就是节点数最多有两个，删除操作简单。
             *    2.3、如果删除节点只有一个后代，则该后代就是替换节点。
             *         2.3.1、如果删除节点和替换节点不是双黑，则直接交换键值，删除替换节点。如果有一个节点是黑色，则染黑删除节点。
             *         2.3.2、如果删除节点和替换节点是双黑，进行双黑处理。
             *
             * 双黑处理：
             */
            --this.size;
            Node replacement;
            if (p.left != null && p.right != null) { // 如果被删除节点的left和right都不为空。即情况1.
                replacement = successor(p); // successor本来可能会寻到父节点以上的节点，但是因为p.left&right!=NIL，所以一定是子节点以下的节点。
                p.val = replacement.val;
                p = replacement;
                /*
                 *    p(V1)                        p(V2)
                 *    /  \                         /   \
                 *  ...  ...          ===>>>     ...   ...
                 *       /                              /
                 *   replacement(V2)                  p(V1)
                 */
            }

            replacement = p.left != null ? p.left : p.right;
            if (replacement != null) { // 2.3  如果删除节点只有一个后代，且该后代是替换节点。
                // 将replacement替换p节点。
                replacement.parent = p.parent;
                if (p.parent == null) {
                    this.root = replacement;
                } else if (p == p.parent.left) {
                    p.parent.left = replacement;
                } else {
                    p.parent.right = replacement;
                }

                p.left = p.right = p.parent = null;
                if (p.isBlack) { // 如果被删除的节点是黑色节点，则需要考虑如何保持红黑性质的问题。即解决双黑问题。
                    this.fixAfterDeletion(replacement);
                }
            } else if (p.parent == null) { // 2.2 如果删除节点是root，且root没有后代。因为如果有后代，就会在前面的if循环里处理。
                this.root = null;
            } else { // 2.1 如果删除节点没有后代，即替换节点是null
                if (p.isBlack) {
                    this.fixAfterDeletion(p);
                }
                // 将节点p和红黑树脱离，完成删除。
                if (p.parent != null) {
                    if (p == p.parent.left) {
                        p.parent.left = null;
                    } else if (p == p.parent.right) {
                        p.parent.right = null;
                    }

                    p.parent = null;
                }
            }
        }
    }


    /**
     * 清空红黑树
     */
    public void clear() {
        this.size = 0;
        this.root = null;
    }

    /**
     * 寻找比节点t的值大的所有节点中最小的节点。即以t为中，寻找中序遍历的下一个节点。
     * @param t 当前访问为“中”的节点
     * @return 下一个节点。
     */
    static Node successor(Node t) {
        if (t == null) {
            return null;
        } else {
            Node p;
            if (t.right != null) {
                for(p = t.right; p.left != null; p = p.left) {
                }

                return p;
            } else {
                p = t.parent;

                for(Node ch = t; p != null && ch == p.right; p = p.parent) {
                    ch = p;
                }

                return p;
            }
        }
    }


    /**
     * 寻找所有比节点t的值小的节点中最大的节点，即以t为中，中序遍历的上一个节点。
     * @param t 节点t
     * @return 上一个节点。
     */
    static Node predecessor(Node t) {
        if (t == null) {
            return null;
        } else {
            Node p;
            if (t.left != null) {
                for(p = t.left; p.right != null; p = p.right) {
                }

                return p;
            } else {
                p = t.parent;

                for(Node ch = t; p != null && ch == p.left; p = p.parent) {
                    ch = p;
                }

                return p;
            }
        }
    }

    /**
     * 判断节点p的颜色。如果p==null，则默认为黑色
     * @param p 节点
     * @return 返回p的颜色
     */
    private static boolean colorOf(Node p) {
        return p == null ? true : p.isBlack;
    }

    /**
     * 寻找节点p的父节点。如果p==null，父节点也是null
     * @param p 节点
     * @return 返回p的父节点。
     */
    private static Node parentOf(Node p) {
        return p == null ? null : p.parent;
    }

    /**
     * 设置颜色。
     */
    private static void setColor(Node p, boolean c) {
        if (p != null) {
            p.isBlack = c;
        }

    }

    /**
     * 寻找节点p的左子节点。如果p==null，则左子节点也是null
     * @param p 节点
     * @return 返回p的左子节点。
     */
    private static Node leftOf(Node p) {
        return p == null ? null : p.left;
    }


    /**
     * 寻找节点p的右子节点。如果p==null，则右子节点也是null。
     * @param p 节点
     * @return 返回p的右子节点。
     */
    private static Node rightOf(Node p) {
        return p == null ? null : p.right;
    }

    /**
     * 对节点p和p.right进行左旋操作：
     *        p.parent（可以不存在）          p.parent
     *         /    \                         /    \
     *   whatever    p          ==>    whatever    p.right
     *             /  \                          / \
     *         p.left   p.right                 p  p.right.right
     *               /  \                     /  \
     *   p.right.left  p.right.right     p.left  p.right.left
     *
     * 在左旋之前，可以得到的大小关系有： p.left < p < p.right.left < p.right < p.right.right
     * 左旋之后，可以得到新的大小关系有： p.left < p < p.right.left < p.right < p.right.right
     * 可见左旋前后节点的关系是完全相等的。
     * @param p 要被左旋的节点
     */
    private void rotateLeft(Node p) {
        if (p != null) {
            Node r = p.right;
            p.right = r.left;
            if (r.left != null) {
                r.left.parent = p;
            }

            r.parent = p.parent;
            if (p.parent == null) {
                this.root = r;
            } else if (p.parent.left == p) {
                p.parent.left = r;
            } else {
                p.parent.right = r;
            }

            r.left = p;
            p.parent = r;
        }

    }


    /**
     * 对节点p和p.left进行右旋操作：
     *                 p.parent（可以不存在）                  p.parent
     *                   /    \                              /     \
     *                  p     whatever    ==>            p.left   whatever
     *               /    \                              /  \
     *          p.left   p.right              p.left.left    p
     *           /  \                                      /  \
     * p.left.left  p.left.right                p.left.right  p.right
     *
     * 在右旋之前，可以得到的大小关系有：     p.left.left < p.left < p.left.right < p < p.right
     * 右旋之后，依然可以得到同样的大小关系：  p.left.left < p.left < p.left.right < p < p.right
     * @param p 要被右旋的节点
     */
    private void rotateRight(Node p) {
        if (p != null) {
            Node l = p.left;
            p.left = l.right;
            if (l.right != null) {
                l.right.parent = p;
            }

            l.parent = p.parent;
            if (p.parent == null) {
                this.root = l;
            } else if (p.parent.right == p) {
                p.parent.right = l;
            } else {
                p.parent.left = l;
            }

            l.right = p;
            p.parent = l;
        }

    }

    private void fixAfterInsertion(Node x) {
        x.isBlack = false;// 先令x的颜色为红色。所有插入节点默认都为红色，这样的调整代价比较小。
        // 如果节点非NIL，非root，并且父节点是红色，则需要进行调整；
        // 否则，无需调整。

        while(x != null && x != this.root && !x.parent.isBlack) {
            Node y;
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) { // 如果是x.parent是x.grandpa的左儿子
                y = rightOf(parentOf(parentOf(x)));
                /*
                 * 此时的情况如下：
                 *     x.parent.parent(黑)
                 *       /       \
                 *  x.parent(红)  y(红|黑)
                 *      |
                 *     x(红)
                 */
                if (!colorOf(y)) { // 如果y是红色
                    /*
                     * 此时应做如下改变：
                     *     x.parent.parent(黑)             x.parent.parent(红)
                     *       /       \         变色            /           \
                     *  x.parent(红)  y(红)   ====>>>   x.parent(黑)      y(黑)
                     *      |                                |
                     *     x(红)                           x(红)
                     *
                     * 然后令 x -> x.parent.parent，继续向上做检测。
                     */
                    setColor(parentOf(x), true);
                    setColor(y, true);
                    setColor(parentOf(parentOf(x)), false);
                    x = parentOf(parentOf(x));
                } else { // 如果y是黑色，y是黑色也包含y==null的情况
                    if (x == rightOf(parentOf(x))) {// 先判断x, x.parent和x.grandpa是不是折线形
                        /*
                         * 此时应将x.parent左旋，形成直线形：
                         *     x.parent.parent(黑)                    x.parent.parent(黑)
                         *       /       \           x.parent左旋          /           \
                         *  x.parent(红)  y(黑)         ====>>>         x(红)         y(黑)
                         *        \                                     /
                         *       x(红)                           x.parent(红)
                         */
                        x = parentOf(x);
                        this.rotateLeft(x);
                    }
                    /*
                     * 此时x, x.parent和x.grandpa形成直线形，需要右旋：
                     *     x.parent.parent(黑)          x.parent.parent(红)                  x.parent(黑)
                     *       /       \          变色          /     \     x.grandpa右旋       /    \
                     *  x.parent(红)  y(黑)   ====>>>  x.parent(黑)  y(黑)    ===>>>      x(红)  x.parent.parent(红)
                     *     /                                /                                        \
                     *   x(红)                           x(红)                                       y(黑)
                     *
                     * 处理之后，红黑树性质修复成功。
                     */
                    setColor(parentOf(x), true);
                    setColor(parentOf(parentOf(x)), false);
                    this.rotateRight(parentOf(parentOf(x)));
                }
            } else { // 如果是x.parent是x.grandpa的右儿子
                y = leftOf(parentOf(parentOf(x)));
                /*
                 * 此时的情况如下：
                 *     x.parent.parent(黑)
                 *       /       \
                 *  y(红|黑)    x.parent(红)
                 *                 |
                 *               x(红)
                 */
                if (!colorOf(y)) {  // 如果y是红色
                    /*
                     * 此时应做如下改变(x.grandpa的黑色下沉到左右子树，交换红色上来)：
                     *     x.parent.parent(黑)                x.parent.parent(红)
                     *       /       \             变色         /           \
                     *    y(红)     x.parent(红)  ====>>>    y(黑)       x.parent(黑)
                     *                |                                     |
                     *               x(红)                                 x(红)
                     *
                     * 然后令 x -> x.parent.parent，继续向上做检测。
                     */
                    setColor(parentOf(x), true);
                    setColor(y, true);
                    setColor(parentOf(parentOf(x)), false);
                    x = parentOf(parentOf(x));
                } else {   // 如果y是黑色
                    if (x == leftOf(parentOf(x))) { // 先判断x, x.parent和x.grandpa是不是折线形
                        /*
                         * 此时应将x.parent右旋，形成直线形：
                         *     x.parent.parent(黑)                    x.parent.parent(黑)
                         *       /       \             x.parent右旋        /           \
                         *    y(黑)   x.parent(红)         ====>>>      y(黑)         x(红)
                         *              /                                                \
                         *            x(红)                                          x.parent(红)
                         */
                        x = parentOf(x);
                        this.rotateRight(x);
                    }
                    /*
                     * 此时x, x.parent和x.grandpa形成直线形，需要左旋：
                     * x.parent.parent(黑)          x.parent.parent(红)                        x.parent(黑)
                     *    /       \           变色      /       \         左旋                   /      \
                     *  y(黑)  x.parent(红)   ===>>>  y(黑)  x.parent(黑)  ===>>> x.parent.parent(红)   x(红)
                     *              \                            \                            /
                     *             x(红)                         x(红)                      y(黑)
                     */
                    setColor(parentOf(x), true);
                    setColor(parentOf(parentOf(x)), false);
                    this.rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        // 最后，确保root是黑色，因为前面有可能会改变root颜色。
        this.root.isBlack = true;
    }


    /**
     * 删除操作后续。
     * @param x 要被直接删除的节点。
     */
    private void fixAfterDeletion(Node x) {
        while(x != this.root && colorOf(x)) { // 如果x非root节点，且颜色是黑色，就需要继续做修复
            Node bro; // 定义x的兄弟节点。该节点可能是null。
            if (x == leftOf(parentOf(x))) { // 如果x是父节点的左子节点
                bro = rightOf(parentOf(x));
                if (!colorOf(bro)) { // 如果bro节点是红色节点，一定不为null。则染红父亲，染黑bro，然后旋转。这样把新的bro变为黑色
                    /*
                     * 下述的操作如下：
                     * x.parent(黑)  变色      x.parent(红)  左旋          bro(黑)
                     *  /    \      ===>>>    /     \       ===>>>       /     \
                     * x(黑) bro(红)        x(黑)  bro(黑)         x.parent(红)  br
                     *      /  \                   /  \              /    \
                     * bl(黑)  br(黑)          bl(黑)  br(黑)      x(黑)   bl(黑)-->bro(黑)
                     */
                    // 变色
                    setColor(bro, true);
                    setColor(parentOf(x), false);
                    // 左旋
                    this.rotateLeft(parentOf(x));
                    // 重定义
                    bro = rightOf(parentOf(x));
                }
                // 此时bro为黑。x和bro同为黑的情况下，可以通过将bro染红来满足x被删之后，x.parent子树满足“任意路径黑色节点数目相同”的性质
                if (colorOf(leftOf(bro)) && colorOf(rightOf(bro))) {
                    // 如果bro, bro.left, bro.right都是黑色。此时将bro染红对bro的子树不会产生影响，但是x.parent子树路径的
                    // 黑色节点数目由于x的删除，由n变为n-1。因此，需要向上继续传递双黑冲突，进一步递归解决。
                    /*
                     *   x.parent                   /---->  x.parent
                     *    /   \                     \        /    \
                     *  x(黑) bro(黑)  ===>>>        -------x(黑) bro(红)
                     *        /  \                               /  \
                     *   bl(黑)   br(黑)                     bl(黑)   br(黑)
                     */
                    setColor(bro, false);
                    x = parentOf(x);
                } else {
                    // 如果bro.left和bro.right中至少有一个为红色。此时将bro染红对bro的子树会产生影响。因此需要同时解决bro上下
                    // 两个方向上的关系。
                    if (colorOf(rightOf(bro))) { // 如果只有bro的右子节点为黑色，bro的左子节点为红色
                        // bro: 黑->红    bro.left: 红->黑
                        /*
                         * x.parent、bro、和bro.left(红)形成折线型，要先染色+旋转成下述的直线型，再做处理。
                         * x.parent                 x.parent                 x.parent
                         *    /  \                    /  \                     /  \
                         *  x(黑) bro(黑)   ===>>>  x(黑) bro(红)  ===>>>   x(黑)  bl(黑)->bro
                         *        /  \                     /  \                      \
                         *    bl(红)  br(黑)            bl(黑)  br(黑)               bro(红)
                         *                                                             \
                         *                                                             br(黑)
                         */
                        setColor(leftOf(bro), true);
                        setColor(bro, false);
                        this.rotateRight(bro);
                        bro = rightOf(parentOf(x));
                    }
                    // 此时有：
                    // x:黑  bro:黑  bro.right:红  bro.left:红|黑
                    /*
                     * x.parent、bro、和bro.right(红)形成直线型，需要染色+旋转进行调整。
                     * x.parent(未知1)         x.parent(黑)                 bro(未知1)
                     *   /  \                   /  \                          /     \
                     * x(黑) bro(黑)   ===>>> x(黑) bro(未知1)  ===>>> x.parent(黑)  br(黑)
                     *       /  \                   /  \                /     \
                     *      bl  br(红)             bl  br(黑)         x(黑)    bl
                     */
                    setColor(bro, colorOf(parentOf(x)));
                    setColor(parentOf(x), true);
                    setColor(rightOf(bro), true);
                    this.rotateLeft(parentOf(x));
                    x = this.root;  // 相当于break，跳出循环。而且该函数最后一行还有设置x颜色为黑的操作。
                }
            } else { // 如果x是父节点的右子节点
                bro = leftOf(parentOf(x));
                if (!colorOf(bro)) { // 如果bro是红色节点
                    /* x:黑  bro:红  x.parent:黑  bro.left:黑  bro.right:黑
                     *       x.parent(黑)              x.parent(红)             bro(黑)
                     *         /     \       变色         /     \    右旋        /     \
                     *     bro(红)  x(黑)    ===>>>   bro(黑)  x(黑) ===>>>   bl(黑)   x.parent(红)
                     *     /    \                     /    \                           /   \
                     *  bl(黑)  br(黑)             bl(黑)  br(黑)                   br(黑)  x(黑)
                     */
                    // 变色
                    setColor(bro, true);
                    setColor(parentOf(x), false);
                    // 右旋
                    this.rotateRight(parentOf(x));
                    // 重定义
                    bro = leftOf(parentOf(x));
                }
                // 此时 x:黑  x.bro:黑
                if (colorOf(rightOf(bro)) && colorOf(leftOf(bro))) { // 如果bro的左右子孩子都是黑色
                    /*
                     *        x.parent                    x.parent <----
                     *         /   \                      /    \        \
                     *    bro(黑) x(黑)  ===>>>        bro(红) x(黑) ----/
                     *      /  \                       /  \
                     * bl(黑)   br(黑)            bl(黑)   br(黑)
                     */
                    setColor(bro, false);
                    x = parentOf(x);
                } else { // 如果bro的左右子孩子不全是黑色
                    if (colorOf(leftOf(bro))) {  // 如果left是黑色，right是红色
                        /*
                         *    x.parent(未知1)         x.parent(未知1)           x.parent(未知1)
                         *       /       \               /       \              /       \
                         *     bro(黑)   x(黑)  ===>>> bro(红)   x(黑) ===>>>  br(黑)   x(黑)
                         *      /   \                  /    \                  /
                         *  bl(黑)  br(红)           bl(黑)  br(黑)          bro(红)
                         *                                                    /
                         *                                                 bl(黑)
                         */
                        setColor(rightOf(bro), true);
                        setColor(bro, false);
                        this.rotateLeft(bro);
                        bro = leftOf(parentOf(x));
                    }
                    // x:黑  x.bro:黑 bro.left:红 bro.right:黑|红
                    /*     x.parent(未知1)              x.parent(黑)              bro(未知1)
                     *         /     \                    /     \                  /      \
                     *     bro(黑)  x(黑)   ===>>>  bro(未知1)  x(黑)  ===>>>    bl(黑)   x.parent(黑)
                     *      /   \                     /    \                              /    \
                     *  bl(红)  br(未知2)           bl(黑)  br(未知2)                br(未知2)  x(黑)
                     */
                    // 变色
                    setColor(bro, colorOf(parentOf(x)));
                    setColor(parentOf(x), true);
                    setColor(leftOf(bro), true);
                    // 右旋
                    this.rotateRight(parentOf(x));
                    // 调整结束
                    x = this.root;
                }
            }
        }
        // 将x染黑。
        setColor(x, true);
    }


    /***************************************************************************************************************/
    /**
     * 返回中序遍历的字符串。
     * @return
     */
    public String strValues() {
        if (this.root == null) {
            return "[]";
        }
        Node node = this.root;
        while (predecessor(node) != null)
            node = predecessor(node);
        StringBuilder sb = new StringBuilder("[");
        while (node != null) {
            sb.append(node.val).append(",");
            node = successor(node);
        }
        sb.append("]");
        return sb.toString();
    }

    public static String strTreeMapKeys(TreeMap<Integer, Integer> tm) {
        if (tm.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int val : tm.keySet()) {
            sb.append(val).append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 测试红黑树
     */
    public static void main(String[] args) {
        try {
            RedBlackTreeFromJDK rbt = new RedBlackTreeFromJDK();
            TreeMap<Integer, Integer> tm = new TreeMap<>();
            HashSet<Integer> set = new HashSet<>();
            int num = 10000;
            for (int seed=0; seed<10000; seed++) {
                Random random = new Random(234);
                for (int i = 0; i < num; i++) {
                    int r = random.nextInt();
                    rbt.add(r);
                    tm.put(r, 0);
                    set.add(r);
                    String s1 = rbt.strValues();
                    String s2 = strTreeMapKeys(tm);
                    System.out.printf("增加值：\t\t\t%d(%d)\n", r, rbt.size());
                    System.out.printf("红黑树输出：\t\t%s\nTreeMap输出：\t%s\n两者是否相等：\t%s\n\n", s1, s2, s1.equals(s2));
                    if (!s1.equals(s2)) {
                        throw new RuntimeException("Error!!!");
                    }
                }

                for (int val : set) {
                    rbt.remove(val);
                    tm.remove(val);
                    String s1 = rbt.strValues();
                    String s2 = strTreeMapKeys(tm);
                    System.out.printf("删除值：\t\t\t%d\n", val);
                    System.out.printf("红黑树输出：\t\t%s\nTreeMap输出：\t%s\n两者是否相等：\t%s\n\n", s1, s2, s1.equals(s2));
                    if (!s1.equals(s2)) {
                        throw new RuntimeException("Error!!!");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
