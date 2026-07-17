import React from 'react';
import {
  Modal,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';
import { CONFIG } from '../config';
import { buildPdfViewerHtml } from '../utils/pdfViewerBridge';

type Props = {
  visible: boolean;
  title: string;
  base64: string;
  onClose: () => void;
};

export default function PdfViewerModal({ visible, title, base64, onClose }: Props) {
  const insets = useSafeAreaInsets();
  const html = React.useMemo(() => buildPdfViewerHtml(base64), [base64]);

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
        <View style={styles.header}>
          <Text style={styles.title} numberOfLines={1}>
            {title || 'Document'}
          </Text>
          <TouchableOpacity style={styles.closeButton} onPress={onClose} accessibilityRole="button">
            <Text style={styles.closeText}>Close</Text>
          </TouchableOpacity>
        </View>
        <WebView
          source={{ html }}
          style={styles.webview}
          originWhitelist={['*']}
          javaScriptEnabled={false}
          scalesPageToFit
          showsVerticalScrollIndicator
        />
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: CONFIG.COLORS.WHITE,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#d1d5db',
    backgroundColor: CONFIG.COLORS.WHITE,
  },
  title: {
    flex: 1,
    fontSize: CONFIG.FONT_SIZES.LARGE,
    fontWeight: '600',
    color: CONFIG.COLORS.DARK_GRAY,
    marginRight: 12,
  },
  closeButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: CONFIG.DIMENSIONS.BORDER_RADIUS,
    backgroundColor: CONFIG.COLORS.PRIMARY,
  },
  closeText: {
    color: CONFIG.COLORS.WHITE,
    fontSize: CONFIG.FONT_SIZES.MEDIUM,
    fontWeight: '600',
  },
  webview: {
    flex: 1,
    backgroundColor: '#525659',
  },
});
