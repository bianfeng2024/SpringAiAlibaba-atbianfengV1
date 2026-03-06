
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class DFSBinaryTree {

    // ==================== 递归版 DFS ====================
    // 前序遍历：根 → 左 → 右
    public List<Integer> preorderDFS(TreeNode root) {
        List<Integer> result = new ArrayList<>();
        preorderHelper(root, result);
        return result;
    }

    private void preorderHelper(TreeNode node, List<Integer> result) {
        if (node == null) {
            return; // 递归终止条件：节点为空
        }
        result.add(node.val); // 1. 访问根节点
        preorderHelper(node.left, result); // 2. 遍历左子树
        preorderHelper(node.right, result); // 3. 遍历右子树
    }

    // 中序遍历：左 → 根 → 右
    public List<Integer> inorderDFS(TreeNode root) {
        List<Integer> result = new ArrayList<>();
        inorderHelper(root, result);
        return result;
    }

    private void inorderHelper(TreeNode node, List<Integer> result) {
        if (node == null) {
            return;
        }
        inorderHelper(node.left, result); // 1. 遍历左子树
        result.add(node.val); // 2. 访问根节点
        inorderHelper(node.right, result); // 3. 遍历右子树
    }

    // 后序遍历：左 → 右 → 根
    public List<Integer> postorderDFS(TreeNode root) {
        List<Integer> result = new ArrayList<>();
        postorderHelper(root, result);
        return result;
    }

    private void postorderHelper(TreeNode node, List<Integer> result) {
        if (node == null) {
            return;
        }
        postorderHelper(node.left, result); // 1. 遍历左子树
        postorderHelper(node.right, result); // 2. 遍历右子树
        result.add(node.val); // 3. 访问根节点
    }

    // ==================== 迭代版 DFS（前序） ====================
    // 手动模拟栈，避免递归栈溢出问题
    public List<Integer> preorderDFSIterative(TreeNode root) {
        List<Integer> result = new ArrayList<>();
        if (root == null) {
            return result;
        }

        Stack<TreeNode> stack = new Stack<>();
        stack.push(root); // 初始化栈，压入根节点

        while (!stack.isEmpty()) {
            TreeNode node = stack.pop(); // 弹出栈顶节点
            result.add(node.val); // 访问当前节点

            // 栈是“后进先出”，所以先压右子树，再压左子树，保证左子树先被访问
            if (node.right != null) {
                stack.push(node.right);
            }
            if (node.left != null) {
                stack.push(node.left);
            }
        }
        return result;
    }

    // ==================== 测试用例 ====================
    public static void main(String[] args) {
        // 构建测试二叉树：
        // 1
        // / \
        // 2 3
        // / \
        // 4 5
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.left.right = new TreeNode(5);

        DFSBinaryTree dfs = new DFSBinaryTree();

        // 测试递归版
        System.out.println("递归版前序遍历：" + dfs.preorderDFS(root)); // [1, 2, 4, 5, 3]
        System.out.println("递归版中序遍历：" + dfs.inorderDFS(root)); // [4, 2, 5, 1, 3]
        System.out.println("递归版后序遍历：" + dfs.postorderDFS(root)); // [4, 5, 2, 3, 1]

        // 测试迭代版
        System.out.println("迭代版前序遍历：" + dfs.preorderDFSIterative(root)); // [1, 2, 4, 5, 3]
    }

    static class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        // 构造方法
        TreeNode() {
        }

        TreeNode(int val) {
            this.val = val;
        }

        TreeNode(int val, TreeNode left, TreeNode right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }
    }
}
