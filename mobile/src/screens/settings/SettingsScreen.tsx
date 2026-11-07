import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Alert } from 'react-native';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { logout } from '../../store/slices/authSlice';
import { toggleTheme } from '../../store/slices/themeSlice';
import Button from '../../components/ui/Button';
import { spacing, typography } from '../../theme';
import { BRAND_COLORS } from '../../theme/colors';

const SettingsScreen = () => {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector(state => state.auth);
  const { mode } = useAppSelector(state => state.theme);

  const handleLogout = () => {
    Alert.alert('Logout', 'Are you sure you want to logout?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Logout',
        style: 'destructive',
        onPress: () => dispatch(logout()),
      },
    ]);
  };

  const handleToggleTheme = () => {
    dispatch(toggleTheme());
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Settings</Text>
        {user && (
          <View style={styles.userInfo}>
            <Text style={styles.userName}>{user.name}</Text>
            <Text style={styles.userEmail}>{user.email}</Text>
          </View>
        )}
      </View>

      <View style={styles.section}>
        <TouchableOpacity style={styles.settingItem} onPress={handleToggleTheme}>
          <Text style={styles.settingLabel}>Theme</Text>
          <Text style={styles.settingValue}>
            {mode === 'light' ? 'Light' : 'Dark'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.settingItem}>
          <Text style={styles.settingLabel}>Help & Support</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.settingItem}>
          <Text style={styles.settingLabel}>About</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.footer}>
        <Button
          title="Logout"
          onPress={handleLogout}
          variant="outline"
          fullWidth
        />
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    backgroundColor: '#FFFFFF',
    padding: spacing.xl,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E5',
  },
  title: {
    ...typography.h1,
    color: BRAND_COLORS.primary,
    marginBottom: spacing.md,
  },
  userInfo: {
    marginTop: spacing.sm,
  },
  userName: {
    ...typography.h3,
    color: '#1A3C34',
    marginBottom: spacing.xs,
  },
  userEmail: {
    ...typography.body,
    color: '#5A6C67',
  },
  section: {
    backgroundColor: '#FFFFFF',
    marginTop: spacing.md,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: '#E5E5E5',
  },
  settingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E5E5',
  },
  settingLabel: {
    ...typography.body,
    color: '#1A3C34',
    fontWeight: '500',
  },
  settingValue: {
    ...typography.body,
    color: '#5A6C67',
  },
  footer: {
    padding: spacing.xl,
    marginTop: 'auto',
  },
});

export default SettingsScreen;
