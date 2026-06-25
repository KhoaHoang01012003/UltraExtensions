package com.crawlfilter;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RepeaterTabCloser
{
    private static final Set<String> NON_REQUEST_TAB_TITLES = Set.of(
            "repeater",
            "request",
            "response",
            "raw",
            "pretty",
            "hex",
            "params",
            "headers",
            "rendered",
            "inspector"
    );

    private RepeaterTabCloser()
    {
    }

    public static CloseResult closeAllOpenRepeaterTabs()
    {
        List<JTabbedPane> repeaterItemPanes = findRepeaterItemPanes();
        int closedTabs = 0;

        for (JTabbedPane pane : repeaterItemPanes)
        {
            closedTabs += closeTabsInPane(pane);
        }

        return new CloseResult(closedTabs, repeaterItemPanes.size());
    }

    private static List<JTabbedPane> findRepeaterItemPanes()
    {
        Set<JTabbedPane> repeaterItemPanes = new LinkedHashSet<>();

        for (Window window : Window.getWindows())
        {
            if (!window.isDisplayable())
            {
                continue;
            }

            collectRepeaterItemPanes(window, repeaterItemPanes);
        }

        return new ArrayList<>(repeaterItemPanes);
    }

    private static void collectRepeaterItemPanes(Component root, Set<JTabbedPane> repeaterItemPanes)
    {
        if (root instanceof JTabbedPane tabbedPane)
        {
            for (int tabIndex = 0; tabIndex < tabbedPane.getTabCount(); tabIndex++)
            {
                if (containsRepeaterText(tabbedPane.getTitleAt(tabIndex)))
                {
                    collectClosableTabPanes(tabbedPane.getComponentAt(tabIndex), repeaterItemPanes);
                }
            }
        }

        if (containsRepeaterHint(root))
        {
            collectClosableTabPanes(root, repeaterItemPanes);
        }

        if (root instanceof Container container)
        {
            for (Component child : container.getComponents())
            {
                collectRepeaterItemPanes(child, repeaterItemPanes);
            }
        }
    }

    private static void collectClosableTabPanes(Component root, Set<JTabbedPane> repeaterItemPanes)
    {
        if (root instanceof JTabbedPane tabbedPane && isLikelyRepeaterItemPane(tabbedPane))
        {
            repeaterItemPanes.add(tabbedPane);
        }

        if (root instanceof Container container)
        {
            for (Component child : container.getComponents())
            {
                collectClosableTabPanes(child, repeaterItemPanes);
            }
        }
    }

    private static boolean isLikelyRepeaterItemPane(JTabbedPane tabbedPane)
    {
        if (tabbedPane.getTabCount() == 0)
        {
            return false;
        }

        for (int tabIndex = 0; tabIndex < tabbedPane.getTabCount(); tabIndex++)
        {
            if (findCloseButton(tabbedPane.getTabComponentAt(tabIndex)) != null)
            {
                return true;
            }

            if (isLikelyRepeaterRequestTabTitle(tabbedPane.getTitleAt(tabIndex)))
            {
                return true;
            }
        }

        return false;
    }

    private static int closeTabsInPane(JTabbedPane tabbedPane)
    {
        int closedTabs = 0;
        boolean changed;

        do
        {
            changed = false;

            for (int tabIndex = tabbedPane.getTabCount() - 1; tabIndex >= 0; tabIndex--)
            {
                if (tabIndex >= tabbedPane.getTabCount())
                {
                    continue;
                }

                String title = tabbedPane.getTitleAt(tabIndex);
                AbstractButton initialCloseButton = findCloseButton(tabbedPane.getTabComponentAt(tabIndex));
                if (initialCloseButton == null && !isLikelyRepeaterRequestTabTitle(title))
                {
                    continue;
                }

                tabbedPane.setSelectedIndex(tabIndex);
                AbstractButton closeButton = findCloseButton(tabbedPane.getTabComponentAt(tabIndex));
                int beforeCount = tabbedPane.getTabCount();
                if (closeButton != null)
                {
                    closeButton.doClick();
                }
                else
                {
                    tabbedPane.removeTabAt(tabIndex);
                }
                int afterCount = tabbedPane.getTabCount();

                if (afterCount < beforeCount)
                {
                    closedTabs += beforeCount - afterCount;
                    changed = true;
                }
            }
        }
        while (changed);

        return closedTabs;
    }

    private static boolean isLikelyRepeaterRequestTabTitle(String title)
    {
        if (title == null)
        {
            return false;
        }

        String normalizedTitle = title.trim().toLowerCase(Locale.ROOT);
        if (normalizedTitle.isEmpty() || NON_REQUEST_TAB_TITLES.contains(normalizedTitle))
        {
            return false;
        }

        return true;
    }

    private static boolean containsRepeaterHint(Component component)
    {
        if (containsRepeaterText(component.getName()))
        {
            return true;
        }

        if (component instanceof JLabel label && containsRepeaterText(label.getText()))
        {
            return true;
        }

        if (component instanceof AbstractButton button && containsRepeaterText(button.getText()))
        {
            return true;
        }

        if (component instanceof JComponent swingComponent)
        {
            if (containsRepeaterText(swingComponent.getToolTipText()))
            {
                return true;
            }

            if (swingComponent.getBorder() instanceof TitledBorder titledBorder
                    && containsRepeaterText(titledBorder.getTitle()))
            {
                return true;
            }
        }

        return containsRepeaterText(component.getClass().getName());
    }

    private static boolean containsRepeaterText(String text)
    {
        return text != null && text.toLowerCase(Locale.ROOT).contains("repeater");
    }

    private static AbstractButton findCloseButton(Component root)
    {
        if (root instanceof AbstractButton button)
        {
            return button;
        }

        if (root instanceof Container container)
        {
            for (Component child : container.getComponents())
            {
                AbstractButton button = findCloseButton(child);
                if (button != null)
                {
                    return button;
                }
            }
        }

        return null;
    }

    public record CloseResult(int closedTabs, int panesVisited)
    {
    }
}
