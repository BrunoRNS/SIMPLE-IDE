package io.simpleide.ui.treefile;

import java.awt.Component;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import io.simpleide.frame.utils.LoadIcon;

final class TreeFileCellRenderer extends DefaultTreeCellRenderer {
  private static final long serialVersionUID = 1L;
  private static final Icon CLOSED_FOLDER_ICON = LoadIcon.loadingIcon("/io/simpleide/icon/folder.png");
  private static final Icon OPEN_FOLDER_ICON = LoadIcon.loadingIcon("/io/simpleide/icon/folder-open.png");
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
    icons.put(TypeFile.JAVA, LoadIcon.loadingIcon("/io/simpleide/icon/java.png"));
    icons.put(TypeFile.GROOVY, LoadIcon.loadingIcon("/io/simpleide/icon/groovy.png"));
    icons.put(TypeFile.KOTLIN, LoadIcon.loadingIcon("/io/simpleide/icon/kotlin.png"));
    icons.put(TypeFile.JAVASCRIPT, LoadIcon.loadingIcon("/io/simpleide/icon/javascript.png"));
    icons.put(TypeFile.PYTHON, LoadIcon.loadingIcon("/io/simpleide/icon/python.png"));
    return icons;
  }
}
