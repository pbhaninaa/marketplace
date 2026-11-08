import { createApp } from 'vue';
import { createPinia } from 'pinia';
import AppSelect from './components/ui/AppSelect.vue';
import App from './App.vue';
import router from './router';
import './styles.css';
// Opt-in "Glass" display style; every rule is scoped to <html data-display="glass">.
import './styles/forms.css';
import './styles/glass.css';
import './styles/viz.css';
import { useDisplayStyleStore } from './stores/displayStyle';
import { installGlassTilt } from './composables/useGlassTilt';

const app = createApp(App);
// Every dropdown in the app: styled like the other inputs (see components/ui/AppSelect.vue).
app.component('AppSelect', AppSelect);
const pinia = createPinia();
app.use(pinia);
// Apply the signed-in user's saved display style before first paint; follows sign-in/out.
useDisplayStyleStore(pinia).init();
installGlassTilt();
app.use(router);
app.mount('#app');
