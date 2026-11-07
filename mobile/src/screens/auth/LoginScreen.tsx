import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  Alert,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useAppDispatch, useAppSelector } from '../../store/hooks';
import { login, clearError } from '../../store/slices/authSlice';
import Button from '../../components/ui/Button';
import Input from '../../components/ui/Input';
import { spacing, typography } from '../../theme';
import { BRAND_COLORS } from '../../theme/colors';

const LoginScreen = () => {
  const navigation = useNavigation();
  const dispatch = useAppDispatch();
  const { loading, error } = useAppSelector(state => state.auth);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');

  useEffect(() => {
    return () => {
      dispatch(clearError());
    };
  }, [dispatch]);

  useEffect(() => {
    if (error) {
      Alert.alert('Login Failed', error);
    }
  }, [error]);

  const validateForm = (): boolean => {
    let isValid = true;

    // Reset errors
    setEmailError('');
    setPasswordError('');

    // Email validation
    if (!email.trim()) {
      setEmailError('Email is required');
      isValid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setEmailError('Invalid email address');
      isValid = false;
    }

    // Password validation
    if (!password) {
      setPasswordError('Password is required');
      isValid = false;
    } else if (password.length < 6) {
      setPasswordError('Password must be at least 6 characters');
      isValid = false;
    }

    return isValid;
  };

  const handleLogin = async () => {
    if (!validateForm()) return;

    try {
      await dispatch(login({ email: email.trim(), password })).unwrap();
      // Navigation will happen automatically via RootNavigator
    } catch (err) {
      // Error is handled by Redux and shown via Alert
    }
  };

  const handleRegister = () => {
    navigation.navigate('Register' as never);
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
      >
        <View style={styles.header}>
          <Text style={styles.title}>Agri Marketplace</Text>
          <Text style={styles.subtitle}>Equipment & Stock: Rental & Resale</Text>
        </View>

        <View style={styles.form}>
          <Text style={styles.formTitle}>Welcome Back</Text>
          <Text style={styles.formSubtitle}>Sign in to continue</Text>

          <Input
            label="Email"
            value={email}
            onChangeText={(text) => {
              setEmail(text);
              setEmailError('');
            }}
            placeholder="Enter your email"
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            leftIcon="email-outline"
            error={emailError}
            editable={!loading}
          />

          <Input
            label="Password"
            value={password}
            onChangeText={(text) => {
              setPassword(text);
              setPasswordError('');
            }}
            placeholder="Enter your password"
            secureTextEntry
            autoCapitalize="none"
            leftIcon="lock-outline"
            error={passwordError}
            editable={!loading}
          />

          <Button
            title="Sign In"
            onPress={handleLogin}
            loading={loading}
            fullWidth
            style={styles.loginButton}
          />

          <TouchableOpacity
            style={styles.forgotPassword}
            disabled={loading}
          >
            <Text style={styles.forgotPasswordText}>Forgot Password?</Text>
          </TouchableOpacity>

          <View style={styles.divider}>
            <View style={styles.dividerLine} />
            <Text style={styles.dividerText}>OR</Text>
            <View style={styles.dividerLine} />
          </View>

          <Button
            title="Create Account"
            onPress={handleRegister}
            variant="outline"
            fullWidth
            disabled={loading}
          />

          <TouchableOpacity
            style={styles.guestButton}
            onPress={() => {/* Continue as guest logic */}}
            disabled={loading}
          >
            <Text style={styles.guestButtonText}>Continue as Guest</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.xxl * 2,
    paddingBottom: spacing.xl,
  },
  header: {
    alignItems: 'center',
    marginBottom: spacing.xxl,
  },
  title: {
    ...typography.h1,
    color: BRAND_COLORS.primary,
    marginBottom: spacing.xs,
  },
  subtitle: {
    ...typography.bodySmall,
    color: '#5A6C67',
    textAlign: 'center',
  },
  form: {
    flex: 1,
  },
  formTitle: {
    ...typography.h2,
    color: '#1A3C34',
    marginBottom: spacing.xs,
  },
  formSubtitle: {
    ...typography.body,
    color: '#5A6C67',
    marginBottom: spacing.xl,
  },
  loginButton: {
    marginTop: spacing.md,
  },
  forgotPassword: {
    alignSelf: 'flex-end',
    marginTop: spacing.md,
    marginBottom: spacing.lg,
  },
  forgotPasswordText: {
    ...typography.bodySmall,
    color: BRAND_COLORS.primary,
    fontWeight: '600',
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: spacing.xl,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: '#E0E0E0',
  },
  dividerText: {
    ...typography.bodySmall,
    color: '#999',
    marginHorizontal: spacing.md,
    fontWeight: '600',
  },
  guestButton: {
    marginTop: spacing.lg,
    alignItems: 'center',
    padding: spacing.md,
  },
  guestButtonText: {
    ...typography.body,
    color: '#5A6C67',
    fontWeight: '600',
  },
});

export default LoginScreen;
