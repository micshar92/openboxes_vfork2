import React from 'react';

import PropTypes from 'prop-types';

import Translate from 'utils/Translate';

const Button = ({
  label, defaultLabel, disabled, variant, type, onClick, EndIcon, isDropdown,
}) => {
  const buttonClass = 'd-flex justify-content-around align-items-center gap-8';
  const variantClass = `${variant}-button`;
  const dropDownClass = `${isDropdown ? 'dropdown-toggle' : ''}`;

  return (
    <button
      className={[variantClass, buttonClass, dropDownClass].join(' ')}
      disabled={disabled}
      type={type}
      onClick={onClick}
      data-toggle={isDropdown && 'dropdown'}
      aria-haspopup={isDropdown && 'true'}
      aria-expanded={isDropdown && 'false'}
    >
      <React.Fragment>
        <Translate id={label} defaultMessage={defaultLabel} />
        {EndIcon && EndIcon}
      </React.Fragment>
    </button>
  );
};

export default Button;

Button.propTypes = {
  label: PropTypes.string.isRequired,
  defaultLabel: PropTypes.string.isRequired,
  disabled: PropTypes.bool,
  type: PropTypes.string,
  variant: PropTypes.string,
  onClick: PropTypes.func,
  EndIcon: PropTypes.element,
  isDropdown: PropTypes.bool,
};

Button.defaultProps = {
  disabled: false,
  isDropdown: false,
  type: 'button',
  variant: 'primary',
  onClick: undefined,
  EndIcon: null,
};
