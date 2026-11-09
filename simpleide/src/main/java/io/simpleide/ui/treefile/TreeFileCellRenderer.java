package io.simpleide.ui.treefile;

import java.awt.Component;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import io.simpleide.utils.ResourceUtil;

final class TreeFileCellRenderer extends DefaultTreeCellRenderer {
  private static final long serialVersionUID = 1L;
  private static final Icon CLOSED_FOLDER_ICON = loadIcon("folder.png");
  private static final Icon OPEN_FOLDER_ICON = loadIcon("folder-open.png");
  private static final Map<TypeFile, Icon> FILE_ICONS = loadFileIcons();

  @Override
  public Component getTreeCellRendererComponent(
      JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof TreeFile file) {
      if (file instanceof Directory) {
        setIcon(expanded ? OPEN_FOLDER_ICON : CLOSED_FOLDER_ICON);
      } else if (file instanceof File fileEntry) {
        Icon icon = FILE_ICONS.get(fileEntry.getTypeFile());
        if (icon != null) {
          setIcon(icon);
        }
      }
    }
    return this;
  }

  private static Map<TypeFile, Icon> loadFileIcons() {
    Map<TypeFile, Icon> icons = new EnumMap<>(TypeFile.class);
    icons.put(TypeFile.JAVA, loadIcon("java.png"));
    icons.put(TypeFile.GROOVY, loadIcon("groovy.png"));
    icons.put(TypeFile.KOTLIN, loadIcon("kotlin.png"));
    icons.put(TypeFile.JAVASCRIPT, loadIcon("javascript.png"));
    icons.put(TypeFile.PYTHON, loadIcon("python.png"));
    icons.put(TypeFile.CPP, loadIcon("cpp.png"));
    icons.put(TypeFile.C, loadIcon("c.png"));
    return icons;
  }

  private static Icon loadIcon(String fileName) {
    try {
      return new ImageIcon(ResourceUtil.getResourcePath("io.simpleide.icon", fileName).toString());
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load file icon: " + fileName, exception);
    }
  }
}
