import React from 'react';
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { CONFIG } from '../config';
import { MARKETPLACE_PRIMARY } from '../config/branding';
import type { BottomNavTab } from '../navigation/bottomNavConfig';
import {
  badgeCountForKey,
  formatBadgeCount,
  type NavBadgeCounts,
} from '../utils/navBadgesBridge';

const ICON_FALLBACKS: Record<string, string> = {
  'calendar-plus-outline': 'calendar-plus',
  'tune-variant': 'tune',
  'timeline-text': 'history',
};

function resolveIcon(name: string): string {
  return ICON_FALLBACKS[name] ?? name;
}

type Props = {
  tabs: BottomNavTab[];
  activeTabId: string;
  onTabPress: (tab: BottomNavTab) => void;
  navBadgeCounts?: NavBadgeCounts;
};

export default function AppBottomNav({
  tabs,
  activeTabId,
  onTabPress,
  navBadgeCounts = {},
}: Props) {
  const insets = useSafeAreaInsets();
  const scrollable = tabs.length >= 6;

  const renderTab = (tab: BottomNavTab) => {
    const active = tab.id === activeTabId;
    const badgeCount = badgeCountForKey(navBadgeCounts, tab.badgeKey);
    const badgeLabel = formatBadgeCount(badgeCount);

    return (
      <TouchableOpacity
        key={tab.id}
        style={[styles.item, scrollable && styles.itemScroll, active && styles.itemActive]}
        accessibilityRole="button"
        accessibilityLabel={
          badgeCount > 0 ? `${tab.label}, ${badgeCount} unread` : tab.label
        }
        accessibilityState={{ selected: active }}
        onPress={() => onTabPress(tab)}
      >
        <View style={[styles.iconWrap, active && styles.iconWrapActive]}>
          <Icon
            name={resolveIcon(tab.icon)}
            size={24}
            color={active ? MARKETPLACE_PRIMARY : CONFIG.COLORS.GRAY}
          />
          {badgeLabel ? (
            <View style={styles.badge}>
              <Text style={styles.badgeText}>{badgeLabel}</Text>
            </View>
          ) : null}
        </View>
        <Text style={[styles.label, active && styles.labelActive]} numberOfLines={1}>
          {tab.label}
        </Text>
      </TouchableOpacity>
    );
  };

  return (
    <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, 10) }]}>
      {scrollable ? (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.scrollContent}
        >
          {tabs.map(renderTab)}
        </ScrollView>
      ) : (
        <View style={styles.row}>{tabs.map(renderTab)}</View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(26,60,52,0.08)',
    backgroundColor: CONFIG.COLORS.WHITE,
    paddingTop: 10,
    paddingHorizontal: 10,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    shadowColor: '#10241f',
    shadowOffset: { width: 0, height: -8 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 18,
  },
  row: {
    flexDirection: 'row',
    gap: 4,
  },
  scrollContent: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 2,
    gap: 4,
  },
  item: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 60,
    paddingHorizontal: 6,
    paddingVertical: 6,
    borderRadius: 16,
  },
  itemScroll: {
    flexGrow: 0,
    flexShrink: 0,
    minWidth: 72,
    paddingHorizontal: 6,
  },
  itemActive: {
    backgroundColor: 'rgba(26,60,52,0.08)',
  },
  iconWrap: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 17,
  },
  iconWrapActive: {
    backgroundColor: 'rgba(26,60,52,0.10)',
  },
  badge: {
    position: 'absolute',
    top: -2,
    right: -8,
    minWidth: 18,
    height: 18,
    paddingHorizontal: 4,
    borderRadius: 9,
    backgroundColor: CONFIG.COLORS.ERROR,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: CONFIG.COLORS.WHITE,
  },
  badgeText: {
    color: CONFIG.COLORS.WHITE,
    fontSize: 10,
    fontWeight: '700',
    lineHeight: 12,
  },
  label: {
    fontSize: 11,
    marginTop: 4,
    color: CONFIG.COLORS.GRAY,
    fontWeight: '500',
  },
  labelActive: {
    color: MARKETPLACE_PRIMARY,
    fontWeight: '700',
  },
});
