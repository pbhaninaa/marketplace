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
import { register, clearError } from '../../store/slices/authSlice';
import Button from '../../components/ui/Button';
import Input from '../../components/ui/Input';
import { spacing, typography } from '../../theme';
import { BRAND_COLORS } from '../../theme/colors';
import { CONFIG } from '../../constants/config';

const RegisterScreen = () => {
  const navigation = useNavigation();
  const dispatch = useAppDispatch();
  const { loading, error } = useAppSelector(state => state.auth);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [errors, setErrors] = useState({
    name: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
  });

  useEffect(() => {
    return () => {
      dispatch(clearError());
    };
  }, [dispatch]);

  useEffect(() => {
    if (error) {
      Alert.alert('Registration Failed', error);
    }
  }, [error]);

  const validateForm = (): boolean => {
    let isValid = true;
    const newErrors = {
      name: '',
      email: '',
      phone: '',
      password: '',
      confirmPassword: '',
    };

    // Name validation
    if (!name.trim()) {
      newErrors.name = 'Name is required';
      isValid = false;
    } else if (name.trim().length < 2) {
      newErrors.name = 'Name must be at least 2 characters';
      isValid = false;
    }

    // Email validation
    if (!email.trim()) {
      newErrors.email = 'Email is required';
      isValid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = 'Invalid email address';
      isValid = false;
    }

    // Phone validation (optional but validated if provided)
    if (phone && !/^[0-9+\-\s()]+$/.test(phone)) {
      newErrors.phone = 'Invalid phone number';
      isValid = false;
    }

    // Password validation
    if (!password) {
      newErrors.password = 'Password is required';
      isValid = false;
    } else if (password.length < CONFIG.MIN_PASSWORD_LENGTH) {
      newErrors.password = `Password must be at least ${CONFIG.MIN_PASSWORD_LENGTH} characters`;
      isValid = false;
    }

    // Confirm password validation
    if (!confirmPassword) {
      newErrors.confirmPassword = 'Please confirm your password';
      isValid = false;
    } else if (password !== confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
      isValid = false;
    }

    setErrors(newErrors);
    return isValid;
  };

  const handleRegister = async () => {
    if (!validateForm()) return;

    try {
      await dispatch(
        register({
          name: name.trim(),
          email: email.trim(),
          password,
          phone: phone.trim() || undefined,
        })
      ).unwrap();
      // Navigation will happen automatically via RootNavigator
    } catch (err) {
      // Error is handled by Redux and shown via Alert
    }
  };

  const handleBackToLogin = () => {
    navigation.goBack();
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
          <Text style={styles.title}>Create Account</Text>
          <Text style={styles.subtitle}>Join Agri Marketplace today</Text>
        </View>

        <View style={styles.form}>
          <Input
            label="Full Name"
            value={name}
            onChangeText={(text) => {
              setName(text);
              setErrors({ ...errors, name: '' });
            }}
            placeholder="Enter your full name"
            autoCapitalize="words"
            leftIcon="account-outline"
            error={errors.name}
            editable={!loading}
          />

          <Input
            label="Email"
            value={email}
            onChangeText={(text) => {
              setEmail(text);
              setErrors({ ...errors, email: '' });
            }}
            placeholder="Enter your email"
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            leftIcon="email-outline"
            error={errors.email}
            editable={!loading}
          />

          <Input
            label="Phone (Optional)"
            value={phone}
            onChangeText={(text) => {
              setPhone(text);
              setErrors({ ...errors, phone: '' });
            }}
            placeholder="Enter your phone number"
            keyboardType="phone-pad"
            leftIcon="phone-outline"
            error={errors.phone}
            editable={!loading}
          />

          <Input
            label="Password"
            value={password}
            onChangeText={(text) => {
              setPassword(text);
              setErrors({ ...errors, password: '' });
            }}
            placeholder="Create a password"
            secureTextEntry
            autoCapitalize="none"
            leftIcon="lock-outline"
            error={errors.password}
            editable={!loading}
          />

          <Input
            label="Confirm Password"
            value={confirmPassword}
            onChangeText={(text) => {
              setConfirmPassword(text);
              setErrors({ ...errors, confirmPassword: '' });
            }}
            placeholder="Confirm your password"
            secureTextEntry
            autoCapitalize="none"
            leftIcon="lock-check-outline"
            error={errors.confirmPassword}
            editable={!loading}
          />

          <Button
            title="Create Account"
            onPress={handleRegister}
            loading={loading}
            fullWidth
            style={styles.registerButton}
          />

          <View style={styles.loginPrompt}>
            <Text style={styles.loginPromptText}>Already have an account? </Text>
            <TouchableOpacity onPress={handleBackToLogin} disabled={loading}>
              <Text style={styles.loginLink}>Sign In</Text>
            </TouchableOpacity>
          </View>
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
    marginBottom: spacing.xl,
  },
  title: {
    ...typography.h1,
    color: BRAND_COLORS.primary,
    marginBottom: spacing.xs,
  },
  subtitle: {
    ...typography.body,
    color: '#5A6C67',
    textAlign: 'center',
  },
  form: {
    flex: 1,
  },
  registerButton: {
    marginTop: spacing.lg,
  },
  loginPrompt: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: spacing.xl,
  },
  loginPromptText: {
    ...typography.body,
    color: '#5A6C67',
  },
  loginLink: {
    ...typography.body,
    color: BRAND_COLORS.primary,
    fontWeight: '600',
  },
});

export default RegisterScreen;
