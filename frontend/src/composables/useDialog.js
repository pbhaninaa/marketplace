import { ref } from 'vue';

const dialogState = ref({
  isOpen: false,
  title: '',
  message: '',
  type: 'info',
  confirmText: 'OK',
  cancelText: 'Cancel',
  showCancel: false,
  onConfirm: null,
  onCancel: null,
});

export function useDialog() {
  function showDialog(options) {
    return new Promise((resolve) => {
      dialogState.value = {
        isOpen: true,
        title: options.title || '',
        message: options.message || '',
        type: options.type || 'info',
        confirmText: options.confirmText || 'OK',
        cancelText: options.cancelText || 'Cancel',
        showCancel: options.showCancel !== undefined ? options.showCancel : false,
        onConfirm: () => {
          dialogState.value.isOpen = false;
          resolve(true);
        },
        onCancel: () => {
          dialogState.value.isOpen = false;
          resolve(false);
        },
      };
    });
  }

  function confirm(message, title = 'Confirm') {
    return showDialog({
      title,
      message,
      type: 'confirm',
      confirmText: 'Confirm',
      cancelText: 'Cancel',
      showCancel: true,
    });
  }

  function alert(message, title = '', type = 'info') {
    return showDialog({
      title,
      message,
      type,
      confirmText: 'OK',
      showCancel: false,
    });
  }

  function success(message, title = 'Success') {
    return alert(message, title, 'success');
  }

  function error(message, title = 'Error') {
    return alert(message, title, 'error');
  }

  function warning(message, title = 'Warning') {
    return alert(message, title, 'warning');
  }

  function closeDialog() {
    dialogState.value.isOpen = false;
  }

  return {
    dialogState,
    showDialog,
    confirm,
    alert,
    success,
    error,
    warning,
    closeDialog,
  };
}
