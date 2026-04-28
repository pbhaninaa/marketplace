# Reusable Components Guide

## Overview

This guide documents all reusable UI components available in the Marketplace frontend. These components ensure consistent styling, behavior, and theme color usage across the entire application.

**Color Theme**: All components use CSS variables from `src/styles.css` to ensure visual consistency.

---

## Color Palette Reference

### Primary Colors (Main Theme)
- `--color-canopy`: `#1a3c34` (Dark forest green - primary button, headers)
- `--color-canopy-mid`: `#2d5a4e` (Mid-tone green - hover states)
- `--color-sage`: `#3d7a66` (Lighter green - accents, secondary elements)
- `--color-sage-soft`: `#e4f0eb` (Soft green - backgrounds)

### Secondary Colors
- `--color-earth`: `#8c6239` (Brown - secondary info)
- `--color-wheat`: `#c9a227` (Golden - highlights)
- `--color-wheat-soft`: `#f5edd8` (Light wheat - backgrounds)

### Status Colors
- Success: `--color-success-bg`: `#e8f4ec` / `--color-success-text`: `#1a4d2e`
- Danger: `--color-danger-bg`: `#fcefea` / `--color-danger-text`: `#8b2c1f`
- Info: `--color-info-bg`: `#e8f1fc` / `--color-info-text`: `#154a7a`

### Neutral Colors
- `--color-text`: `#1c2418` (Main text)
- `--color-text-secondary`: `#4a5545` (Secondary text)
- `--color-muted`: `#6b7568` (Muted/disabled text)
- `--color-border`: `#d9d3c7` (Borders)

---

## Components

### 1. **BaseButton** (`src/components/ui/BaseButton.vue`)

Universal button component with multiple variants and sizes.

#### Props:
- `variant`: `'primary'` | `'secondary'` | `'ghost'` | `'danger'` | `'success'` (default: `'primary'`)
- `size`: `'sm'` | `'md'` | `'lg'` (default: `'md'`)
- `disabled`: `boolean` (default: `false`)
- `type`: `'button'` | `'submit'` | `'reset'` (default: `'button'`)
- `isLoading`: `boolean` (default: `false`)
- `icon`: `string` - Material icon name (default: `''`)
- `iconPosition`: `'left'` | `'right'` (default: `'left'`)

#### Usage Examples:

```vue
<!-- Primary button -->
<BaseButton @click="handleSave">Save</BaseButton>

<!-- Secondary button with icon -->
<BaseButton variant="secondary" icon="edit">Edit</BaseButton>

<!-- Danger button, small size -->
<BaseButton variant="danger" size="sm" @click="deleteItem">Delete</BaseButton>

<!-- Ghost button (transparent) -->
<BaseButton variant="ghost">Cancel</BaseButton>

<!-- Loading state -->
<BaseButton :is-loading="saving" @click="submit">{{ saving ? 'Saving' : 'Submit' }}</BaseButton>

<!-- Disabled -->
<BaseButton :disabled="!hasChanges">Apply</BaseButton>
```

#### Color Reference:
- Primary: Canopy green (`--color-canopy`)
- Secondary: Sage soft background with sage border
- Ghost: Transparent with border
- Danger: Red (`#8b2c1f`)
- Success: Green (`#1a4d2e`)

---

### 2. **BaseInput** (`src/components/ui/BaseInput.vue`)

Text input component with validation and theme support.

#### Props:
- `modelValue`: `string` | `number` (v-model)
- `type`: `'text'` | `'email'` | `'password'` | `'number'` | `'tel'` (default: `'text'`)
- `placeholder`: `string`
- `label`: `string`
- `error`: `string` (shows error message if provided)
- `disabled`: `boolean`
- `readonly`: `boolean`
- `required`: `boolean`
- `size`: `'sm'` | `'md'` | `'lg'` (default: `'md'`)

#### Events:
- `@update:modelValue` - Input value changed
- `@blur` - Input lost focus
- `@focus` - Input gained focus
- `@change` - Value changed

#### Usage Examples:

```vue
<!-- Basic input with label -->
<BaseInput v-model="name" label="Full Name" placeholder="Enter your name" />

<!-- Email input with error -->
<BaseInput
  v-model="email"
  type="email"
  label="Email"
  :error="emailError"
  required
/>

<!-- Number input, small size -->
<BaseInput
  v-model="quantity"
  type="number"
  label="Quantity"
  size="sm"
/>

<!-- Disabled input -->
<BaseInput v-model="readOnly" label="Status" disabled />
```

#### Color Reference:
- Border: `--color-border`
- Focus border: `--color-canopy`
- Error border: `--color-danger-text`
- Text: `--color-text`

---

### 3. **BaseSelect** (`src/components/ui/BaseSelect.vue`)

Select dropdown component with consistent styling.

#### Props:
- `modelValue`: `string` | `number` (v-model)
- `options`: `Array` - Array of `{ value: string/number, label: string }`
- `label`: `string`
- `error`: `string`
- `disabled`: `boolean`
- `required`: `boolean`
- `size`: `'sm'` | `'md'` | `'lg'`

#### Events:
- `@update:modelValue` - Selection changed
- `@change` - Selection changed

#### Usage Examples:

```vue
<!-- Basic select -->
<BaseSelect
  v-model="selectedType"
  label="Listing Type"
  :options="[
    { value: 'SALE', label: 'For Sale' },
    { value: 'RENT', label: 'For Rent' }
  ]"
/>

<!-- With error state -->
<BaseSelect
  v-model="category"
  label="Category"
  :options="categories"
  :error="categoryError"
  required
/>
```

---

### 4. **BaseTextarea** (`src/components/ui/BaseTextarea.vue`)

Multi-line text input with consistent styling.

#### Props:
- `modelValue`: `string` (v-model)
- `placeholder`: `string`
- `label`: `string`
- `error`: `string`
- `disabled`: `boolean`
- `readonly`: `boolean`
- `required`: `boolean`
- `rows`: `number` (default: `4`)

#### Usage Examples:

```vue
<!-- Basic textarea -->
<BaseTextarea v-model="description" label="Description" />

<!-- Textarea with error -->
<BaseTextarea
  v-model="notes"
  label="Notes"
  :error="notesError"
  rows="6"
  required
/>
```

---

### 5. **BaseTable** (`src/components/ui/BaseTable.vue`)

Reusable table component with sorting and theming.

#### Props:
- `columns`: `Array` (required) - Array of `{ key: string, label: string, width?: string }`
- `rows`: `Array` (required) - Array of row objects
- `striped`: `boolean` (default: `true`) - Alternating row colors
- `hoverable`: `boolean` (default: `true`) - Highlight on hover
- `compact`: `boolean` (default: `false`) - Reduced padding
- `emptyMessage`: `string` (default: `'No records found.'`)

#### Events:
- `@row-click` - Row clicked

#### Slots:
- `cell-{columnKey}` - Custom cell rendering

#### Usage Examples:

```vue
<template>
  <!-- Basic table -->
  <BaseTable
    :columns="[
      { key: 'id', label: 'ID', width: '10%' },
      { key: 'name', label: 'Name', width: '50%' },
      { key: 'status', label: 'Status', width: '40%' }
    ]"
    :rows="items"
    @row-click="selectRow"
  />

  <!-- Table with custom cell rendering -->
  <BaseTable
    :columns="columns"
    :rows="users"
    striped
    hoverable
    @row-click="editUser"
  >
    <template #cell-email="{ value }">
      <span class="email-cell">{{ value }}</span>
    </template>
    <template #cell-status="{ row }">
      <span :class="`status-${row.status.toLowerCase()}`">
        {{ row.status }}
      </span>
    </template>
  </BaseTable>
</template>
```

---

### 6. **TextWithTooltip** (`src/components/ui/TextWithTooltip.vue`)

Text component that truncates long content with tooltip.

#### Props:
- `text`: `string` (required)
- `maxLength`: `number` (default: `50`)
- `tag`: `string` (default: `'span'`) - HTML tag to render

#### Usage Examples:

```vue
<!-- Default 50 character limit -->
<TextWithTooltip :text="longTitle" />

<!-- Custom max length -->
<TextWithTooltip :text="description" :max-length="100" />

<!-- Different tag -->
<TextWithTooltip :text="name" tag="strong" />
```

---

## Migration Guide: From Old Classes to New Components

### Before (Old Approach)
```vue
<button type="button" class="btn btn-primary">Save</button>
<button type="button" class="btn btn-ghost">Cancel</button>
<input type="text" placeholder="Name" />
<select>
  <option>Choose</option>
</select>
```

### After (New Approach)
```vue
<BaseButton @click="save">Save</BaseButton>
<BaseButton variant="ghost">Cancel</BaseButton>
<BaseInput v-model="name" placeholder="Name" />
<BaseSelect v-model="choice" :options="options" />
```

---

## Best Practices

### ✅ DO:
- Always use base components for consistent styling
- Use appropriate variants (primary, secondary, ghost, danger)
- Use correct button sizes for context (sm for tables, md for forms, lg for CTAs)
- Provide labels for all form inputs
- Show validation errors inline
- Use theme colors from CSS variables

### ❌ DON'T:
- Don't use inline styles for colors
- Don't create custom button classes
- Don't duplicate input styling
- Don't mix old and new component approaches
- Don't hardcode color values

---

## Component Checklist

- [x] BaseButton - All variants and sizes
- [x] BaseInput - All input types
- [x] BaseSelect - Dropdown selection
- [x] BaseTextarea - Multi-line input
- [x] BaseTable - Data table rendering
- [x] TextWithTooltip - Long text truncation
- [ ] BaseCheckbox - (To be created)
- [ ] BaseRadio - (To be created)
- [ ] BaseModal - (Exists as DialogModal, to be renamed)

---

## Theme Color Constants

Add these to your component `<style scoped>` sections:

```css
/* Primary theme - Canopy Green */
background: var(--color-canopy);        /* #1a3c34 */
color: var(--color-canopy-mid);         /* #2d5a4e */
border: 1px solid var(--color-sage);    /* #3d7a66 */
background: var(--color-sage-soft);     /* #e4f0eb */

/* Status colors */
background: var(--color-success-bg);    /* #e8f4ec */
color: var(--color-danger-text);        /* #8b2c1f */
border: 1px solid var(--color-border);  /* #d9d3c7 */

/* Text */
color: var(--color-text);               /* #1c2418 */
color: var(--color-muted);              /* #6b7568 */
```

---

## Support

For questions or feature requests, update this guide and notify the team.
